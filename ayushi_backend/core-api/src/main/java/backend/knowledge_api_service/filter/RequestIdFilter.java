package backend.knowledge_api_service.filter;

import backend.knowledge_api_service.model.Department;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_USER_ID = "userId";
    public static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final ThreadLocal<CallerInfo> CALLER_HOLDER = new ThreadLocal<>();

    @Value("${jwt.secret:local-dev-jwt-secret-change-me-must-be-at-least-32-bytes}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        CallerInfo caller = resolveCaller(request);
        CALLER_HOLDER.set(caller);
        MDC.put(MDC_TRACE_ID, requestId);
        MDC.put(MDC_USER_ID, caller.userId().toString());
        response.setHeader(HEADER_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CALLER_HOLDER.remove();
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    public static String getCurrentTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    public static CallerInfo getCurrentCaller() {
        CallerInfo caller = CALLER_HOLDER.get();
        if (caller != null) {
            return caller;
        }
        return new CallerInfo(DEFAULT_USER_ID, "ADMIN", Department.GENERAL, List.of(Department.values()));
    }

    private CallerInfo resolveCaller(HttpServletRequest request) {
        // 1. Check Authorization: Bearer <jwt>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
                if (keyBytes.length >= 32) {
                    SecretKey key = Keys.hmacShaKeyFor(keyBytes);
                    Claims claims = Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                    UUID userId = parseUuidOrDefault(claims.getSubject(), DEFAULT_USER_ID);
                    String role = claims.get("role", String.class);
                    String deptStr = claims.get("department", String.class);
                    if (deptStr == null) {
                        deptStr = claims.get("dept", String.class);
                    }
                    Department dept = parseDepartmentOrDefault(deptStr, Department.GENERAL);
                    return new CallerInfo(userId, role != null ? role : "EMPLOYEE", dept, computeAllowedDepartments(role, dept));
                }
            } catch (Exception ignored) {
                // Fallback to header/dev caller until Milestone 4 strict security enforcement
            }
        }

        // 2. Check optional X-User-* headers for testing / development
        UUID userId = parseUuidOrDefault(request.getHeader("X-User-Id"), DEFAULT_USER_ID);
        String role = request.getHeader("X-User-Role");
        String deptHeader = request.getHeader("X-User-Department");
        if (role != null || deptHeader != null) {
            String effectiveRole = role != null ? role.toUpperCase(Locale.ROOT) : "EMPLOYEE";
            Department dept = parseDepartmentOrDefault(deptHeader, Department.GENERAL);
            return new CallerInfo(userId, effectiveRole, dept, computeAllowedDepartments(effectiveRole, dept));
        }

        // Default unauthenticated dev caller (ADMIN access across all departments prior to Milestone 4)
        return new CallerInfo(userId, "ADMIN", Department.GENERAL, List.of(Department.values()));
    }

    public static List<Department> computeAllowedDepartments(String role, Department userDepartment) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return List.of(Department.values());
        }
        Set<Department> allowed = new LinkedHashSet<>();
        if (userDepartment != null) {
            allowed.add(userDepartment);
        }
        allowed.add(Department.GENERAL);
        return new ArrayList<>(allowed);
    }

    private static UUID parseUuidOrDefault(String value, UUID fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Department parseDepartmentOrDefault(String value, Department fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Department.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public record CallerInfo(
            UUID userId,
            String role,
            Department department,
            List<Department> allowedDepartments
    ) {}
}

