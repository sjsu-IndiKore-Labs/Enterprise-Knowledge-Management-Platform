package backend.knowledge_api_service.service;

import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.AuditAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.ekm.common.event.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditEventPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.audit-events-queue:ekm-audit-events}")
    private String auditQueueName;

    private final CopyOnWriteArrayList<AuditEvent> publishedEvents = new CopyOnWriteArrayList<>();

    public void publish(
            AuditAction action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata) {
        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        AuditEvent event = AuditEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("AUDIT_EVENT")
                .occurredAt(Instant.now())
                .traceId(RequestIdFilter.getCurrentTraceId())
                .action(action.name())
                .actorUserId(caller.userId())
                .actorEmail(caller.email())
                .actorRole(caller.role())
                .actorDepartment(caller.department() != null ? caller.department().name() : null)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .metadata(metadata != null ? metadata : Collections.emptyMap())
                .build();
        publishEvent(event);
    }

    public void publishEvent(AuditEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(Instant.now());
        }
        if (event.getTraceId() == null) {
            event.setTraceId(RequestIdFilter.getCurrentTraceId());
        }
        publishedEvents.add(event);

        try {
            String payload = objectMapper.writeValueAsString(event);
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(auditQueueName)
                    .build()).queueUrl();

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(payload)
                    .build());

            log.info("Published AUDIT_EVENT eventId={}, action={}, resourceType={}, resourceId={}",
                    event.getEventId(), event.getAction(), event.getResourceType(), event.getResourceId());
        } catch (Exception ex) {
            log.debug("Audit event recorded locally (SQS queue '{}' unreachable: {}): action={}, eventId={}",
                    auditQueueName, ex.getMessage(), event.getAction(), event.getEventId());
        }
    }

    public List<AuditEvent> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

    public void clearPublishedEvents() {
        publishedEvents.clear();
    }
}

