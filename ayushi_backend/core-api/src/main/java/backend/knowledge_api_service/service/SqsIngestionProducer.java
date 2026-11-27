package backend.knowledge_api_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.ekm.common.event.DocumentIngestRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class SqsIngestionProducer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${sqs.document-ingest-queue:ekm-document-ingest}")
    private String ingestQueueName;

    private final CopyOnWriteArrayList<DocumentIngestRequestedEvent> publishedEvents = new CopyOnWriteArrayList<>();

    public void publishIngestRequested(DocumentIngestRequestedEvent event) {
        publishedEvents.add(event);
        try {
            String payload = objectMapper.writeValueAsString(event);
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(ingestQueueName)
                    .build()).queueUrl();

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(payload)
                    .build());

            log.info("Published DOCUMENT_INGEST_REQUESTED eventId={}, documentId={}, versionId={} to queue={}",
                    event.getEventId(), event.getDocumentId(), event.getVersionId(), ingestQueueName);
        } catch (Exception ex) {
            log.warn("SQS publish to queue '{}' could not reach broker ({}): event recorded locally (eventId={})",
                    ingestQueueName, ex.getMessage(), event.getEventId());
        }
    }

    public List<DocumentIngestRequestedEvent> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

    public void clearPublishedEvents() {
        publishedEvents.clear();
    }
}

