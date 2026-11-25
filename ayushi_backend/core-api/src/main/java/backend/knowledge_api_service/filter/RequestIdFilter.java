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
    public static final UUID DEFAULT_USER_ID = UUID.fromString("11111111-1111-4111-8111-000000000001");

    private static final ThreadLocal<CallerInfo> CALLER_HOLDER = new ThreadLocal<>();

    @Value("${jwt.secret:local-dev-jwt-secret-change-me-must-be-at-least-32-bytes}")
    private String jwtSecret = "local-dev-jwt-secret-change-me-must-be-at-least-32-bytes";

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
        return new CallerInfo(
                DEFAULT_USER_ID,
                "alice.admin@helios.example.com",
                "Alice Admin",
                "ADMIN",
                Department.GENERAL,
                List.of(Department.values()),
                true);
    }

    public static void setCurrentCaller(CallerInfo caller) {
        CALLER_HOLDER.set(caller);
    }

    public static void clearCurrentCaller() {
        CALLER_HOLDER.remove();
    }

    private CallerInfo resolveCaller(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length >= 32) {
                try {
                    SecretKey key = Keys.hmacShaKeyFor(keyBytes);
                    Claims claims = Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                    UUID userId = parseUuidOrDefault(claims.getSubject(), DEFAULT_USER_ID);
                    String email = claims.get("email", String.class);
                    String name = claims.get("name", String.class);
                    String role = claims.get("role", String.class);
                    String deptStr = claims.get("department", String.class);
                    if (deptStr == null) {
                        deptStr = claims.get("dept", String.class);
                    }
                    String effectiveRole = role != null ? role.toUpperCase(Locale.ROOT) : "EMPLOYEE";
                    Department dept = parseDepartmentOrDefault(deptStr, Department.GENERAL);
                    return new CallerInfo(
                            userId,
                            email != null ? email : "user@helios.example.com",
                            name != null ? name : "Enterprise User",
                            effectiveRole,
                            dept,
                            computeAllowedDepartments(effectiveRole, dept),
                            true);
                } catch (Exception e) {
                    return new CallerInfo(
                            DEFAULT_USER_ID,
                            "unauthenticated",
                            "Unauthenticated",
                            "EMPLOYEE",
                            Department.GENERAL,
                            List.of(Department.GENERAL),
                            false);
                }
            }
        }

        // Check optional X-User-* headers for dev/testing
        UUID userId = parseUuidOrDefault(request.getHeader("X-User-Id"), DEFAULT_USER_ID);
        String role = request.getHeader("X-User-Role");
        String deptHeader = request.getHeader("X-User-Department");
        String emailHeader = request.getHeader("X-User-Email");
        if (role != null || deptHeader != null || emailHeader != null) {
            String effectiveRole = role != null ? role.toUpperCase(Locale.ROOT) : "EMPLOYEE";
            Department dept = parseDepartmentOrDefault(deptHeader, Department.GENERAL);
            return new CallerInfo(
                    userId,
                    emailHeader != null ? emailHeader : "dev@helios.example.com",
                    "Dev User",
                    effectiveRole,
                    dept,
                    computeAllowedDepartments(effectiveRole, dept),
                    true);
        }

        // Default dev admin caller when no auth header is sent
        return new CallerInfo(
                userId,
                "alice.admin@helios.example.com",
                "Alice Admin",
                "ADMIN",
                Department.GENERAL,
                List.of(Department.values()),
                false);
    }

    public static List<Department> computeAllowedDepartments(String role, Department userDepartment) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return List.of(
                    Department.GENERAL,
                    Department.HR,
                    Department.ENGINEERING,
                    Department.FINANCE,
                    Department.LEGAL,
                    Department.MARKETING,
                    Department.OPERATIONS);
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
            String email,
            String name,
            String role,
            Department department,
            List<Department> allowedDepartments,
            boolean authenticated
    ) {
        public CallerInfo(UUID userId, String role, Department department, List<Department> allowedDepartments) {
            this(userId, "user@helios.example.com", "User", role, department, allowedDepartments, true);
        }
    }
}
