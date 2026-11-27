package backend.knowledge_api_service.service;

import backend.knowledge_api_service.entity.Document;
import backend.knowledge_api_service.entity.DocumentVersion;
import backend.knowledge_api_service.entity.IngestionJob;
import backend.knowledge_api_service.model.DocumentStatus;
import backend.knowledge_api_service.repository.DocumentChunkRepository;
import backend.knowledge_api_service.repository.DocumentRepository;
import backend.knowledge_api_service.repository.DocumentVersionRepository;
import backend.knowledge_api_service.repository.IngestionJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.ekm.common.event.DocumentIngestStatusEvent;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStatusSqsConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IngestionJobRepository ingestionJobRepository;

    @Value("${sqs.document-status-queue:ekm-document-status}")
    private String statusQueueName;

    @Value("${sqs.status-polling.enabled:true}")
    private boolean pollingEnabled;

    private final Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${sqs.status-polling.delay-ms:3000}")
    public void pollStatusQueue() {
        if (!pollingEnabled) {
            return;
        }
        try {
            String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(statusQueueName)
                    .build()).queueUrl();

            List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(1)
                    .build()).messages();

            for (Message message : messages) {
                try {
                    DocumentIngestStatusEvent event = objectMapper.readValue(
                            message.body(), DocumentIngestStatusEvent.class);
                    handleStatusEvent(event);
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build());
                } catch (Exception ex) {
                    log.error("Failed to process status message from {}: {}", statusQueueName, ex.getMessage(), ex);
                }
            }
        } catch (Exception ignored) {
            // Queue or LocalStack not running in standalone local profile; ignore quietly
        }
    }

    @Transactional
    public boolean handleStatusEvent(DocumentIngestStatusEvent event) {
        if (event == null || event.getEventId() == null || event.getDocumentId() == null) {
            throw new IllegalArgumentException("Status event must contain eventId and documentId");
        }

        // Idempotency check by eventId
        if (!processedEventIds.add(event.getEventId())) {
            log.info("Ignoring duplicate DOCUMENT_INGEST_STATUS eventId={}", event.getEventId());
            return false;
        }

        DocumentStatus newStatus = DocumentStatus.valueOf(event.getStatus().toUpperCase());
        Optional<Document> docOpt = documentRepository.findById(event.getDocumentId());
        if (docOpt.isEmpty()) {
            log.warn("Received status event for unknown documentId={}", event.getDocumentId());
            return false;
        }

        Document doc = docOpt.get();

        // Update DocumentVersion row
        Optional<DocumentVersion> versionOpt = Optional.empty();
        if (event.getVersionId() != null) {
            versionOpt = documentVersionRepository.findById(event.getVersionId());
        }
        if (versionOpt.isEmpty()) {
            versionOpt = documentVersionRepository.findTopByDocumentIdOrderByVersionDesc(doc.getId());
        }

        int targetVersion = doc.getCurrentVersion() != null ? doc.getCurrentVersion() : 1;
        if (versionOpt.isPresent()) {
            DocumentVersion dv = versionOpt.get();
            dv.setStatus(newStatus);
            if (event.getPageCount() != null) {
                dv.setPageCount(event.getPageCount());
            }
            if (event.getChunkCount() != null) {
                dv.setChunkCount(event.getChunkCount());
            }
            if (event.getErrorCode() != null) {
                dv.setErrorCode(event.getErrorCode());
            }
            if (event.getErrorMessage() != null) {
                dv.setErrorMessage(event.getErrorMessage());
            }
            if (newStatus == DocumentStatus.INDEXED) {
                dv.setIndexedAt(event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now());
            }
            documentVersionRepository.save(dv);
            targetVersion = dv.getVersion();
        }

        // Update Document row
        doc.setStatus(newStatus);
        if (event.getPageCount() != null) {
            doc.setPageCount(event.getPageCount());
        }
        if (newStatus == DocumentStatus.INDEXED) {
            doc.setCurrentVersion(targetVersion);
            doc.setVersion(targetVersion);
            documentChunkRepository.markOtherVersionsNotCurrent(doc.getId(), targetVersion);
        }
        documentRepository.save(doc);

        // Update legacy IngestionJob row if present
        ingestionJobRepository.findTopByDocumentIdOrderByCreatedAtDesc(doc.getId()).ifPresent(job -> {
            job.setStatus(newStatus);
            if (event.getProgressPercent() != null) {
                job.setProgressPercent(event.getProgressPercent());
            } else if (newStatus == DocumentStatus.INDEXED) {
                job.setProgressPercent(100);
            } else if (newStatus == DocumentStatus.PROCESSING) {
                job.setProgressPercent(50);
            }
            job.setErrorMessage(event.getErrorMessage());
            ingestionJobRepository.save(job);
        });

        log.info("Processed DOCUMENT_INGEST_STATUS eventId={}, documentId={}, version={}, status={}",
                event.getEventId(), doc.getId(), targetVersion, newStatus);
        return true;
    }
}

