package com.helios.ekm.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.ekm.audit.config.AuditAwsConfig;
import com.helios.ekm.audit.entity.AuditLogRecord;
import com.helios.ekm.audit.entity.KnowledgeGapRecord;
import com.helios.ekm.audit.entity.QueryLogRecord;
import com.helios.ekm.audit.repository.AuditLogRecordRepository;
import com.helios.ekm.audit.repository.KnowledgeGapRecordRepository;
import com.helios.ekm.audit.repository.QueryLogRecordRepository;
import com.helios.ekm.audit.service.AuditEventConsumerService;
import com.helios.ekm.common.event.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventConsumerServiceTest {

    private final List<AuditLogRecord> auditLogs = new ArrayList<>();
    private final List<QueryLogRecord> queryLogs = new ArrayList<>();
    private final Map<String, KnowledgeGapRecord> gaps = new HashMap<>();

    private AuditEventConsumerService consumerService;

    @BeforeEach
    void setUp() {
        auditLogs.clear();
        queryLogs.clear();
        gaps.clear();

        ObjectMapper mapper = new AuditAwsConfig().objectMapper();
        SqsClient sqsClient = (SqsClient) Proxy.newProxyInstance(
                SqsClient.class.getClassLoader(),
                new Class<?>[]{SqsClient.class},
                (p, m, a) -> { throw new RuntimeException("Offline test"); });

        AuditLogRecordRepository auditRepo = (AuditLogRecordRepository) Proxy.newProxyInstance(
                AuditLogRecordRepository.class.getClassLoader(),
                new Class<?>[]{AuditLogRecordRepository.class},
                (p, m, a) -> {
                    if ("existsByEventId".equals(m.getName())) {
                        UUID id = (UUID) a[0];
                        return auditLogs.stream().anyMatch(r -> r.getEventId().equals(id));
                    }
                    if ("save".equals(m.getName())) {
                        AuditLogRecord r = (AuditLogRecord) a[0];
                        auditLogs.add(r);
                        return r;
                    }
                    return null;
                });

        QueryLogRecordRepository queryRepo = (QueryLogRecordRepository) Proxy.newProxyInstance(
                QueryLogRecordRepository.class.getClassLoader(),
                new Class<?>[]{QueryLogRecordRepository.class},
                (p, m, a) -> {
                    if ("save".equals(m.getName())) {
                        QueryLogRecord q = (QueryLogRecord) a[0];
                        queryLogs.add(q);
                        return q;
                    }
                    return null;
                });

        KnowledgeGapRecordRepository gapRepo = (KnowledgeGapRecordRepository) Proxy.newProxyInstance(
                KnowledgeGapRecordRepository.class.getClassLoader(),
                new Class<?>[]{KnowledgeGapRecordRepository.class},
                (p, m, a) -> {
                    if ("findByNormalizedQueryAndDepartmentCode".equals(m.getName())) {
                        String key = a[0] + "::" + a[1];
                        return Optional.ofNullable(gaps.get(key));
                    }
                    if ("save".equals(m.getName())) {
                        KnowledgeGapRecord g = (KnowledgeGapRecord) a[0];
                        String key = g.getNormalizedQuery() + "::" + g.getDepartmentCode();
                        gaps.put(key, g);
                        return g;
                    }
                    return null;
                });

        consumerService = new AuditEventConsumerService(sqsClient, mapper, auditRepo, queryRepo, gapRepo);
    }

    @Test
    @DisplayName("M5 audit-service: Idempotently records audit_logs, query_logs, and upserts knowledge_gaps with occurrences++")
    void auditEventConsumerPersistsLogsAndUpsertsKnowledgeGaps() {
        UUID eventId1 = UUID.randomUUID();
        UUID queryId1 = UUID.randomUUID();

        AuditEvent unansweredEvent1 = AuditEvent.builder()
                .eventId(eventId1)
                .occurredAt(Instant.now())
                .traceId("trace-101")
                .action("QUERY_ASKED")
                .actorUserId(UUID.randomUUID())
                .actorEmail("eric.eng@helios.example.com")
                .actorRole("EMPLOYEE")
                .actorDepartment("ENGINEERING")
                .resourceType("QUERY")
                .resourceId(queryId1.toString())
                .metadata(Map.of(
                        "queryId", queryId1.toString(),
                        "queryText", "  Does Helios offer   PET insurance? ",
                        "answered", false,
                        "confidence", 0.22,
                        "latencyMs", 95,
                        "citedDocumentIds", Collections.emptyList()
                ))
                .build();

        boolean first = consumerService.processAuditEvent(unansweredEvent1);
        boolean duplicate = consumerService.processAuditEvent(unansweredEvent1);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(auditLogs).hasSize(1);
        assertThat(queryLogs).hasSize(1);
        assertThat(queryLogs.get(0).getNormalizedQuery()).isEqualTo("does helios offer pet insurance?");
        assertThat(gaps).hasSize(1);

        KnowledgeGapRecord gap = gaps.get("does helios offer pet insurance?::ENGINEERING");
        assertThat(gap).isNotNull();
        assertThat(gap.getOccurrences()).isEqualTo(1);

        // Second unanswered query with same normalized text -> increments occurrences to 2
        AuditEvent unansweredEvent2 = AuditEvent.builder()
                .eventId(UUID.randomUUID())
                .occurredAt(Instant.now())
                .traceId("trace-102")
                .action("QUERY_ASKED")
                .actorDepartment("ENGINEERING")
                .metadata(Map.of(
                        "queryId", UUID.randomUUID().toString(),
                        "queryText", "does helios offer pet insurance?",
                        "answered", false,
                        "confidence", 0.19,
                        "latencyMs", 88,
                        "citedDocumentIds", Collections.emptyList()
                ))
                .build();

        consumerService.processAuditEvent(unansweredEvent2);
        assertThat(gap.getOccurrences()).isEqualTo(2);
    }
}

