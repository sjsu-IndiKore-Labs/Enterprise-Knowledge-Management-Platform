package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.request.UpdateKnowledgeGapRequest;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.entity.*;
import backend.knowledge_api_service.exception.ResourceNotFoundException;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.*;
import backend.knowledge_api_service.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final QueryLogRepository queryLogRepository;
    private final KnowledgeGapRepository knowledgeGapRepository;
    private final AuditLogRepository auditLogRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AuthService authService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse getSummary(Instant from, Instant to) {
        authService.requireAdmin("ANALYTICS_READ");
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);

        List<QueryLogEntity> logs = queryLogRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(effectiveFrom, effectiveTo);
        long totalQueries = logs.size();
        long answeredCount = logs.stream().filter(q -> Boolean.TRUE.equals(q.getAnswered())).count();
        double answeredRate = totalQueries > 0 ? (double) answeredCount / totalQueries : 0.0;

        List<Integer> latencies = logs.stream()
                .map(QueryLogEntity::getLatencyMs)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        int avgLatencyMs = latencies.isEmpty()
                ? 0
                : (int) Math.round(latencies.stream().mapToInt(Integer::intValue).average().orElse(0));
        int p95LatencyMs = latencies.isEmpty()
                ? 0
                : latencies.get(Math.min(latencies.size() - 1, (int) Math.floor(latencies.size() * 0.95)));

        Map<String, Long> queriesByDepartment = logs.stream()
                .filter(q -> q.getDepartmentCode() != null)
                .collect(Collectors.groupingBy(QueryLogEntity::getDepartmentCode, Collectors.counting()));

        List<Document> allDocs = documentRepository.findAll().stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toList());
        long totalDocs = allDocs.size();
        long indexedDocs = allDocs.stream().filter(d -> d.getStatus() == DocumentStatus.INDEXED).count();
        long failedDocs = allDocs.stream().filter(d -> d.getStatus() == DocumentStatus.FAILED).count();

        long activeUsers = userRepository.countByActiveTrue();
        long openGaps = knowledgeGapRepository.countByStatus(KnowledgeGapStatus.OPEN);

        List<ChatMessageEntity> feedbackMessages = chatMessageRepository.findAll().stream()
                .filter(m -> m.getFeedback() != null && !m.getFeedback().isBlank())
                .collect(Collectors.toList());
        Double positiveFeedbackRate = null;
        if (!feedbackMessages.isEmpty()) {
            long upCount = feedbackMessages.stream().filter(m -> "UP".equalsIgnoreCase(m.getFeedback())).count();
            positiveFeedbackRate = (double) upCount / feedbackMessages.size();
        }

        return AnalyticsSummaryResponse.builder()
                .totalQueries(totalQueries)
                .answeredRate(answeredRate)
                .avgLatencyMs(avgLatencyMs)
                .p95LatencyMs(p95LatencyMs)
                .activeUsers(activeUsers)
                .totalDocuments(totalDocs)
                .indexedDocuments(indexedDocs)
                .failedDocuments(failedDocs)
                .openKnowledgeGaps(openGaps)
                .positiveFeedbackRate(positiveFeedbackRate)
                .queriesByDepartment(queriesByDepartment)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TimeSeriesPointDto> getQueriesOverTime(Instant from, Instant to, Department department) {
        authService.requireAdmin("ANALYTICS_READ");
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);

        List<QueryLogEntity> logs = queryLogRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(effectiveFrom, effectiveTo);
        if (department != null) {
            logs = logs.stream()
                    .filter(q -> department.name().equalsIgnoreCase(q.getDepartmentCode()))
                    .collect(Collectors.toList());
        }

        Map<LocalDate, List<QueryLogEntity>> byDate = logs.stream()
                .collect(Collectors.groupingBy(
                        q -> q.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));

        List<TimeSeriesPointDto> points = new ArrayList<>();
        for (Map.Entry<LocalDate, List<QueryLogEntity>> entry : byDate.entrySet()) {
            long total = entry.getValue().size();
            long unanswered = entry.getValue().stream().filter(q -> !Boolean.TRUE.equals(q.getAnswered())).count();
            points.add(TimeSeriesPointDto.builder()
                    .date(entry.getKey().toString())
                    .queries(total)
                    .unanswered(unanswered)
                    .build());
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<TopQueryDto> getTopQueries(Instant from, Instant to, int limit) {
        authService.requireAdmin("ANALYTICS_READ");
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);
        int maxLimit = Math.min(Math.max(1, limit), 100);

        List<QueryLogEntity> logs = queryLogRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(effectiveFrom, effectiveTo);
        Map<String, List<QueryLogEntity>> grouped = logs.stream()
                .filter(q -> q.getNormalizedQuery() != null && !q.getNormalizedQuery().isBlank())
                .collect(Collectors.groupingBy(QueryLogEntity::getNormalizedQuery));

        return grouped.entrySet().stream()
                .map(e -> {
                    long count = e.getValue().size();
                    long answered = e.getValue().stream().filter(q -> Boolean.TRUE.equals(q.getAnswered())).count();
                    return TopQueryDto.builder()
                            .query(e.getKey())
                            .count(count)
                            .answeredRate(count > 0 ? (double) answered / count : 0.0)
                            .build();
                })
                .sorted(Comparator.comparingLong(TopQueryDto::getCount).reversed())
                .limit(maxLimit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TopDocumentDto> getTopDocuments(Instant from, Instant to, int limit) {
        authService.requireAdmin("ANALYTICS_READ");
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);
        int maxLimit = Math.min(Math.max(1, limit), 100);

        List<QueryLogEntity> logs = queryLogRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(effectiveFrom, effectiveTo);
        Map<UUID, Long> citationCounts = new HashMap<>();

        for (QueryLogEntity logEntity : logs) {
            if (logEntity.getCitedDocumentIds() != null && !logEntity.getCitedDocumentIds().isBlank()) {
                for (String rawId : logEntity.getCitedDocumentIds().split(",")) {
                    try {
                        UUID docId = UUID.fromString(rawId.trim());
                        citationCounts.merge(docId, 1L, Long::sum);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        List<TopDocumentDto> result = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : citationCounts.entrySet()) {
            documentRepository.findById(entry.getKey()).ifPresent(doc -> result.add(
                    TopDocumentDto.builder()
                            .documentId(doc.getId())
                            .title(doc.getTitle())
                            .department(doc.getDepartment())
                            .citationCount(entry.getValue())
                            .build()));
        }

        return result.stream()
                .sorted(Comparator.comparingLong(TopDocumentDto::getCitationCount).reversed())
                .limit(maxLimit)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KnowledgeGapPageResponse listKnowledgeGaps(
            int page, int size, KnowledgeGapStatus status, Department department) {
        authService.requireAdmin("ANALYTICS_READ");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "lastSeenAt"));

        Specification<KnowledgeGapEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (department != null) {
                predicates.add(cb.equal(root.get("departmentCode"), department.name()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<KnowledgeGapEntity> gapPage = knowledgeGapRepository.findAll(spec, pageable);
        List<KnowledgeGapDto> items = gapPage.getContent().stream()
                .map(this::toKnowledgeGapDto)
                .collect(Collectors.toList());

        return KnowledgeGapPageResponse.builder()
                .page(gapPage.getNumber())
                .size(gapPage.getSize())
                .totalItems(gapPage.getTotalElements())
                .totalPages(gapPage.getTotalPages())
                .items(items)
                .build();
    }

    @Transactional
    public KnowledgeGapDto updateKnowledgeGap(UUID gapId, UpdateKnowledgeGapRequest request) {
        authService.requireAdmin("ANALYTICS_READ");
        KnowledgeGapEntity gap = knowledgeGapRepository.findById(gapId)
                .orElseThrow(() -> new ResourceNotFoundException("KNOWLEDGE_GAP_NOT_FOUND",
                        "Knowledge gap not found with ID: " + gapId));

        if (request.getStatus() != null) {
            gap.setStatus(request.getStatus());
            if (request.getStatus() == KnowledgeGapStatus.RESOLVED) {
                gap.setResolvedBy(RequestIdFilter.getCurrentCaller().userId());
                gap.setResolvedAt(Instant.now());
            }
        }
        if (request.getNotes() != null) {
            gap.setNotes(request.getNotes());
        }

        KnowledgeGapEntity saved = knowledgeGapRepository.save(gap);
        auditEventPublisher.publish(
                AuditAction.KNOWLEDGE_GAP_UPDATED,
                "KNOWLEDGE_GAP",
                saved.getId().toString(),
                Map.of("status", saved.getStatus().name()));
        return toKnowledgeGapDto(saved);
    }

    @Transactional(readOnly = true)
    public AuditLogPageResponse listAuditLogs(
            int page, int size, Instant from, Instant to, AuditAction action, UUID userId) {
        authService.requireAdmin("ANALYTICS_READ");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        Specification<AuditLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action.name()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AuditLogEntity> logPage = auditLogRepository.findAll(spec, pageable);
        List<AuditLogDto> items = logPage.getContent().stream()
                .map(this::toAuditLogDto)
                .collect(Collectors.toList());

        return AuditLogPageResponse.builder()
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalItems(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .items(items)
                .build();
    }

    private KnowledgeGapDto toKnowledgeGapDto(KnowledgeGapEntity g) {
        Department dept = Department.GENERAL;
        try {
            if (g.getDepartmentCode() != null) {
                dept = Department.valueOf(g.getDepartmentCode().toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
        }
        return KnowledgeGapDto.builder()
                .id(g.getId())
                .sampleQuery(g.getSampleQuery())
                .normalizedQuery(g.getNormalizedQuery())
                .department(dept)
                .occurrences(g.getOccurrences())
                .status(g.getStatus())
                .notes(g.getNotes())
                .firstSeenAt(g.getFirstSeenAt())
                .lastSeenAt(g.getLastSeenAt())
                .resolvedAt(g.getResolvedAt())
                .build();
    }

    private AuditLogDto toAuditLogDto(AuditLogEntity a) {
        AuditAction action = null;
        try {
            if (a.getAction() != null) {
                action = AuditAction.valueOf(a.getAction());
            }
        } catch (IllegalArgumentException ignored) {
        }

        Role role = null;
        try {
            if (a.getActorRole() != null) {
                role = Role.valueOf(a.getActorRole());
            }
        } catch (IllegalArgumentException ignored) {
        }

        Department dept = null;
        try {
            if (a.getActorDepartment() != null) {
                dept = Department.valueOf(a.getActorDepartment());
            }
        } catch (IllegalArgumentException ignored) {
        }

        Map<String, Object> metadataMap = Collections.emptyMap();
        if (a.getMetadata() != null && !a.getMetadata().isBlank()) {
            try {
                metadataMap = objectMapper.readValue(a.getMetadata(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
            }
        }

        return AuditLogDto.builder()
                .id(a.getId())
                .occurredAt(a.getOccurredAt())
                .action(action)
                .actorUserId(a.getActorUserId())
                .actorEmail(a.getActorEmail())
                .actorRole(role)
                .actorDepartment(dept)
                .resourceType(a.getResourceType())
                .resourceId(a.getResourceId())
                .metadata(metadataMap)
                .ipAddress(a.getIpAddress())
                .build();
    }
}

