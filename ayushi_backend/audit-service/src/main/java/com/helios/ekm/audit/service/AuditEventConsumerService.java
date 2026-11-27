package com.helios.ekm.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.ekm.audit.entity.AuditLogRecord;
import com.helios.ekm.audit.entity.KnowledgeGapRecord;
import com.helios.ekm.audit.entity.QueryLogRecord;
import com.helios.ekm.audit.repository.AuditLogRecordRepository;
import com.helios.ekm.audit.repository.KnowledgeGapRecordRepository;
import com.helios.ekm.audit.repository.QueryLogRecordRepository;
import com.helios.ekm.common.event.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumerService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final AuditLogRecordRepository auditLogRepository;
    private final QueryLogRecordRepository queryLogRepository;
    private final KnowledgeGapRecordRepository knowledgeGapRepository;

    @Value("${sqs.audit-events-queue:ekm-audit-events}")
    private String auditQueueName = "ekm-audit-events";

    @Value("${sqs.audit-polling.enabled:true}")
    private boolean pollingEnabled = true;

    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${sqs.audit-polling.delay-ms:3000}")
    public void pollAuditQueue() {
        if (!pollingEnabled) {
            return;
        }
        try {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(auditQueueName)
                    .build()).queueUrl();

            List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build()).messages();

            for (Message message : messages) {
                try {
                    AuditEvent event = objectMapper.readValue(message.body(), AuditEvent.class);
                    processAuditEvent(event);
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build());
                } catch (Exception ex) {
                    log.error("Failed to process message from {}: {}", auditQueueName, ex.getMessage(), ex);
                }
            }
        } catch (Exception ignored) {
            // Queue or LocalStack not running in standalone local profile
        }
    }

    @Transactional
    public boolean processAuditEvent(AuditEvent event) {
        if (event == null || event.getEventId() == null || event.getAction() == null) {
            throw new IllegalArgumentException("AuditEvent must contain eventId and action");
        }

        // Idempotency check by eventId
        if (!processedEventIds.add(event.getEventId()) || auditLogRepository.existsByEventId(event.getEventId())) {
            log.info("Ignoring duplicate AUDIT_EVENT eventId={}", event.getEventId());
            return false;
        }

        Instant occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();
        String metadataJson = serializeMetadata(event.getMetadata());

        // 1. Write to audit_logs
        AuditLogRecord auditLog = AuditLogRecord.builder()
                .eventId(event.getEventId())
                .occurredAt(occurredAt)
                .action(event.getAction())
                .actorUserId(event.getActorUserId())
                .actorEmail(event.getActorEmail())
                .actorRole(event.getActorRole())
                .actorDepartment(event.getActorDepartment())
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .traceId(event.getTraceId())
                .metadata(metadataJson)
                .build();
        auditLogRepository.save(auditLog);

        // 2. If QUERY_ASKED -> write to query_logs and upsert knowledge_gaps when answered == false
        if ("QUERY_ASKED".equalsIgnoreCase(event.getAction())) {
            handleQueryAsked(event, occurredAt);
        }

        log.info("Processed AUDIT_EVENT eventId={}, action={}", event.getEventId(), event.getAction());
        return true;
    }

    private void handleQueryAsked(AuditEvent event, Instant occurredAt) {
        Map<String, Object> meta = event.getMetadata() != null ? event.getMetadata() : Collections.emptyMap();

        UUID queryId = parseUuid(meta.get("queryId"), UUID.randomUUID());
        UUID conversationId = parseUuid(meta.get("conversationId"), null);
        String queryText = meta.get("queryText") != null ? meta.get("queryText").toString() : "";
        String normalizedQuery = AuditEvent.normalizeQuery(queryText);
        boolean answered = Boolean.TRUE.equals(meta.get("answered"));
        Double confidence = meta.get("confidence") instanceof Number n ? n.doubleValue() : null;
        Integer latencyMs = meta.get("latencyMs") instanceof Number n ? n.intValue() : null;
        String model = meta.get("model") != null ? meta.get("model").toString() : null;
        Integer promptTokens = meta.get("promptTokens") instanceof Number n ? n.intValue() : null;
        Integer completionTokens = meta.get("completionTokens") instanceof Number n ? n.intValue() : null;

        String allowedDeptsStr = joinList(meta.get("allowedDepartments"));
        String citedDocIdsStr = joinList(meta.get("citedDocumentIds"));
        String deptCode = event.getActorDepartment() != null ? event.getActorDepartment() : "GENERAL";

        QueryLogRecord queryLog = QueryLogRecord.builder()
                .id(queryId)
                .userId(event.getActorUserId())
                .departmentCode(deptCode)
                .conversationId(conversationId)
                .queryText(queryText)
                .normalizedQuery(normalizedQuery)
                .allowedDepartments(allowedDeptsStr)
                .answered(answered)
                .confidence(confidence)
                .latencyMs(latencyMs)
                .citedDocumentIds(citedDocIdsStr)
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .createdAt(occurredAt)
                .build();
        queryLogRepository.save(queryLog);

        // Upsert knowledge_gaps when answered == false
        if (!answered && !normalizedQuery.isBlank()) {
            Optional<KnowledgeGapRecord> existingGap = knowledgeGapRepository
                    .findByNormalizedQueryAndDepartmentCode(normalizedQuery, deptCode);
            if (existingGap.isPresent()) {
                KnowledgeGapRecord gap = existingGap.get();
                gap.setOccurrences((gap.getOccurrences() != null ? gap.getOccurrences() : 1) + 1);
                gap.setLastSeenAt(occurredAt);
                knowledgeGapRepository.save(gap);
            } else {
                KnowledgeGapRecord newGap = KnowledgeGapRecord.builder()
                        .normalizedQuery(normalizedQuery)
                        .sampleQuery(queryText)
                        .departmentCode(deptCode)
                        .occurrences(1)
                        .status("OPEN")
                        .firstSeenAt(occurredAt)
                        .lastSeenAt(occurredAt)
                        .build();
                knowledgeGapRepository.save(newGap);
            }
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static UUID parseUuid(Object raw, UUID fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof UUID u) {
            return u;
        }
        try {
            return UUID.fromString(raw.toString().trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String joinList(Object raw) {
        if (raw instanceof Collection<?> col) {
            return col.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
        }
        return raw != null ? raw.toString() : "";
    }
}

