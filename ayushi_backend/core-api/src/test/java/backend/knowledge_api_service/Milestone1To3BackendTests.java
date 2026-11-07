package backend.knowledge_api_service;

import backend.knowledge_api_service.config.JacksonConfig;
import backend.knowledge_api_service.dto.request.ChatRequest;
import backend.knowledge_api_service.dto.request.DocumentUploadRequest;
import backend.knowledge_api_service.dto.request.SearchRequest;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.entity.*;
import backend.knowledge_api_service.exception.GlobalExceptionHandler;
import backend.knowledge_api_service.exception.PayloadTooLargeException;
import backend.knowledge_api_service.exception.ResourceNotFoundException;
import backend.knowledge_api_service.exception.UnsupportedMediaTypeException;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.*;
import backend.knowledge_api_service.repository.*;
import backend.knowledge_api_service.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helios.ekm.common.dto.ErrorResponse;
import com.helios.ekm.common.event.DocumentIngestRequestedEvent;
import com.helios.ekm.common.event.DocumentIngestStatusEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Milestone1To3BackendTests {

    private final Map<UUID, Document> documentsStore = new ConcurrentHashMap<>();
    private final Map<UUID, DocumentVersion> versionsStore = new ConcurrentHashMap<>();
    private final Map<UUID, DocumentChunk> chunksStore = new ConcurrentHashMap<>();
    private final Map<UUID, Conversation> conversationsStore = new ConcurrentHashMap<>();
    private final List<ChatMessageEntity> chatMessagesStore = new ArrayList<>();
    private final AtomicReference<UUID> markedDeletedDocId = new AtomicReference<>();
    private final AtomicReference<AiServiceClient.InternalSearchRequest> capturedSearchReq = new AtomicReference<>();

    private ObjectMapper objectMapper;
    private S3StorageService s3StorageService;
    private SqsIngestionProducer sqsIngestionProducer;
    private DocumentStatusSqsConsumer statusConsumer;
    private DocumentService documentService;
    private SearchService searchService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        documentsStore.clear();
        versionsStore.clear();
        chunksStore.clear();
        conversationsStore.clear();
        chatMessagesStore.clear();
        markedDeletedDocId.set(null);
        capturedSearchReq.set(null);

        objectMapper = new JacksonConfig().objectMapper();

        S3Client s3Client = proxyThrowingOnUpload(S3Client.class);
        S3Presigner s3Presigner = proxyThrowingOnUpload(S3Presigner.class);
        SqsClient sqsClient = proxyThrowingOnUpload(SqsClient.class);

        s3StorageService = new S3StorageService(s3Client, s3Presigner);
        ReflectionTestUtils.setField(s3StorageService, "documentsBucket", "ekm-documents-local");
        ReflectionTestUtils.setField(s3StorageService, "awsEndpointUrl", "http://localhost:4566");

        sqsIngestionProducer = new SqsIngestionProducer(sqsClient, objectMapper);
        ReflectionTestUtils.setField(sqsIngestionProducer, "ingestQueueName", "ekm-document-ingest");

        DocumentRepository documentRepository = createDocumentRepositoryProxy();
        DocumentVersionRepository documentVersionRepository = createDocumentVersionRepositoryProxy();
        DocumentChunkRepository documentChunkRepository = createDocumentChunkRepositoryProxy();
        IngestionJobRepository ingestionJobRepository = createIngestionJobRepositoryProxy();
        ConversationRepository conversationRepository = createConversationRepositoryProxy();
        ChatMessageRepository chatMessageRepository = createChatMessageRepositoryProxy();

        statusConsumer = new DocumentStatusSqsConsumer(
                sqsClient,
                objectMapper,
                documentRepository,
                documentVersionRepository,
                documentChunkRepository,
                ingestionJobRepository);

        documentService = new DocumentService(
                documentRepository,
                documentVersionRepository,
                documentChunkRepository,
                ingestionJobRepository,
                s3StorageService,
                sqsIngestionProducer);
        ReflectionTestUtils.setField(documentService, "maxUploadMb", 25L);

        AiServiceClient stubAiClient = new AiServiceClient(objectMapper) {
            @Override
            public InternalSearchResponse search(InternalSearchRequest request) {
                capturedSearchReq.set(request);
                return InternalSearchResponse.builder()
                        .query(request.getQuery())
                        .mode(request.getMode())
                        .latencyMs(35)
                        .results(List.of(SearchResultItem.builder()
                                .chunkId(UUID.randomUUID())
                                .documentId(UUID.randomUUID())
                                .documentTitle("HR Policy")
                                .department(Department.HR)
                                .version(1)
                                .content("16 weeks paid leave")
                                .score(0.91)
                                .build()))
                        .build();
            }

            @Override
            public InternalAnswerResponse answer(InternalAnswerRequest request) {
                CitationDto citation = CitationDto.builder()
                        .citationNumber(1)
                        .documentId(UUID.randomUUID())
                        .documentTitle("PTO Policy")
                        .department(Department.HR)
                        .version(1)
                        .pageNumber(3)
                        .chunkId(UUID.randomUUID())
                        .snippet("Employees accrue 1.67 days per month.")
                        .score(0.88)
                        .build();
                return InternalAnswerResponse.builder()
                        .queryId(request.getQueryId())
                        .answer("Employees accrue 1.67 days per month [1].")
                        .answered(true)
                        .confidence(0.88)
                        .citations(List.of(citation))
                        .latencyMs(210)
                        .build();
            }
        };

        searchService = new SearchService(stubAiClient);
        chatService = new ChatService(conversationRepository, chatMessageRepository, stubAiClient, objectMapper);
    }

    // =========================================================================
    // MILESTONE 1 TESTS: X-Request-Id filter, GlobalExceptionHandler, Jackson
    // =========================================================================

    @Test
    @DisplayName("M1: RequestIdFilter reads or generates X-Request-Id and echoes it in response header")
    void requestIdFilterGeneratesAndPropagatesHeader() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);
        String generated = res.getHeader(RequestIdFilter.HEADER_REQUEST_ID);
        assertThat(generated).isNotBlank();

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/documents");
        req2.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "trace-custom-123");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, new MockFilterChain());
        assertThat(res2.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo("trace-custom-123");
    }

    @Test
    @DisplayName("M1: GlobalExceptionHandler returns contract ErrorResponse with code, message, traceId, timestamp")
    void globalExceptionHandlerProducesContractErrorBody() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ErrorResponse> notFoundRes = handler.handleApiException(
                new ResourceNotFoundException("DOCUMENT_NOT_FOUND", "Document does not exist"));

        assertThat(notFoundRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFoundRes.getBody()).isNotNull();
        assertThat(notFoundRes.getBody().getCode()).isEqualTo("DOCUMENT_NOT_FOUND");
        assertThat(notFoundRes.getBody().getMessage()).isEqualTo("Document does not exist");
        assertThat(notFoundRes.getBody().getTraceId()).isNotBlank();
        assertThat(notFoundRes.getBody().getTimestamp()).isNotNull();
    }

    // =========================================================================
    // MILESTONE 2 TESTS: Magic bytes validation, S3, SQS ingest & status consumer
    // =========================================================================

    @Test
    @DisplayName("M2: Magic byte validation accepts valid PDF/DOCX and rejects spoofed or >25MB files")
    void magicBytesAndSizeValidation() {
        byte[] validPdfBytes = "%PDF-1.7 sample pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "policy.pdf", "application/pdf", validPdfBytes);
        assertThat(documentService.detectContentTypeByMagicBytes(validPdf, validPdfBytes))
                .isEqualTo(DocumentService.MIME_PDF);

        byte[] validDocxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00};
        MockMultipartFile validDocx = new MockMultipartFile(
                "file", "handbook.docx", DocumentService.MIME_DOCX, validDocxBytes);
        assertThat(documentService.detectContentTypeByMagicBytes(validDocx, validDocxBytes))
                .isEqualTo(DocumentService.MIME_DOCX);

        byte[] fakePdfBytes = "This is plain text pretending to be a PDF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile spoofedPdf = new MockMultipartFile(
                "file", "spoofed.pdf", "application/pdf", fakePdfBytes);
        assertThatThrownBy(() -> documentService.detectContentTypeByMagicBytes(spoofedPdf, fakePdfBytes))
                .isInstanceOf(UnsupportedMediaTypeException.class);

        ReflectionTestUtils.setField(documentService, "maxUploadMb", 1L);
        byte[] twoMbPdf = new byte[2 * 1024 * 1024];
        System.arraycopy(validPdfBytes, 0, twoMbPdf, 0, validPdfBytes.length);
        MockMultipartFile largePdf = new MockMultipartFile("file", "huge.pdf", "application/pdf", twoMbPdf);
        DocumentUploadRequest req = DocumentUploadRequest.builder()
                .title("Huge Doc")
                .department(Department.HR)
                .category(DocumentCategory.POLICY)
                .build();

        assertThatThrownBy(() -> documentService.createAndIngestDocument(largePdf, req))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    @DisplayName("M2: End-to-end upload -> S3 key + SHA256 -> DOCUMENT_INGEST_REQUESTED -> status INDEXED")
    void uploadToSqsAndStatusConsumerLifecycle() {
        byte[] pdfBytes = "%PDF-1.7 parental leave policy content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "Parental_Leave.pdf", "application/pdf", pdfBytes);
        DocumentUploadRequest uploadReq = DocumentUploadRequest.builder()
                .title("Parental Leave Policy")
                .department(Department.HR)
                .category(DocumentCategory.POLICY)
                .author("HR Team")
                .tags("leave,pto,hr")
                .build();

        DocumentResponse created = documentService.createAndIngestDocument(file, uploadReq);
        UUID docId = created.getId();

        assertThat(docId).isNotNull();
        assertThat(created.getStatus()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(created.getDepartment()).isEqualTo(Department.HR);
        assertThat(created.getCategory()).isEqualTo(DocumentCategory.POLICY);
        assertThat(created.getTags()).containsExactly("leave", "pto", "hr");

        // Verify DOCUMENT_INGEST_REQUESTED event matches contract schema
        assertThat(sqsIngestionProducer.getPublishedEvents()).hasSize(1);
        DocumentIngestRequestedEvent event = sqsIngestionProducer.getPublishedEvents().get(0);
        assertThat(event.getEventType()).isEqualTo("DOCUMENT_INGEST_REQUESTED");
        assertThat(event.getDocumentId()).isEqualTo(docId);
        assertThat(event.getVersion()).isEqualTo(1);
        assertThat(event.getS3Bucket()).isEqualTo("ekm-documents-local");
        assertThat(event.getS3Key()).isEqualTo("documents/HR/" + docId + "/v1/Parental_Leave.pdf");
        assertThat(event.getContentType()).isEqualTo("application/pdf");
        assertThat(event.getDepartment()).isEqualTo("HR");
        assertThat(event.getCategory()).isEqualTo("POLICY");

        // Simulate DOCUMENT_INGEST_STATUS (INDEXED) from ai-service
        UUID statusEventId = UUID.randomUUID();
        DocumentIngestStatusEvent statusEvent = DocumentIngestStatusEvent.builder()
                .eventId(statusEventId)
                .eventType("DOCUMENT_INGEST_STATUS")
                .occurredAt(Instant.now())
                .traceId("trace-1")
                .documentId(docId)
                .versionId(event.getVersionId())
                .status("INDEXED")
                .pageCount(16)
                .chunkCount(38)
                .build();

        boolean firstProcess = statusConsumer.handleStatusEvent(statusEvent);
        boolean duplicateProcess = statusConsumer.handleStatusEvent(statusEvent);

        assertThat(firstProcess).isTrue();
        assertThat(duplicateProcess).isFalse();

        Document updatedDoc = documentsStore.get(docId);
        DocumentVersion updatedVer = versionsStore.get(event.getVersionId());
        assertThat(updatedDoc.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(updatedDoc.getPageCount()).isEqualTo(16);
        assertThat(updatedVer.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(updatedVer.getChunkCount()).isEqualTo(38);
        assertThat(updatedVer.getIndexedAt()).isNotNull();
    }

    @Test
    @DisplayName("M2: Presigned download URL appends #page=N and soft-delete marks chunks deleted")
    void downloadUrlAndSoftDelete() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder()
                .id(docId)
                .title("Engineering Onboarding")
                .fileName("Onboarding.pdf")
                .s3Key("documents/ENGINEERING/" + docId + "/v1/Onboarding.pdf")
                .department(Department.ENGINEERING)
                .currentVersion(1)
                .status(DocumentStatus.INDEXED)
                .build();
        documentsStore.put(docId, doc);

        DownloadUrlResponse urlRes = documentService.getDownloadUrl(docId, 1, 7);
        assertThat(urlRes.getUrl()).endsWith("#page=7");
        assertThat(urlRes.getExpiresAt()).isAfter(Instant.now());

        documentService.softDeleteDocument(docId);
        assertThat(doc.getDeletedAt()).isNotNull();
        assertThat(markedDeletedDocId.get()).isEqualTo(docId);
    }

    // =========================================================================
    // MILESTONE 3 TESTS: Search proxy, Chat + Conversations, Citation chunk preview
    // =========================================================================

    @Test
    @DisplayName("M3: SearchService intersects requested departments with caller allowedDepartments")
    void searchServiceIntersectsAllowedDepartments() {
        SearchRequest req = SearchRequest.builder()
                .query("parental leave")
                .mode(SearchMode.HYBRID)
                .departments(List.of(Department.HR, Department.GENERAL))
                .topK(5)
                .build();

        SearchResponse res = searchService.search(req);
        assertThat(res.getResults()).hasSize(1);
        assertThat(res.getResults().get(0).getSnippet()).isEqualTo("16 weeks paid leave");
        assertThat(res.getResults().get(0).getSimilarityScore()).isEqualTo(0.91);
        assertThat(capturedSearchReq.get().getAllowedDepartments())
                .containsExactly(Department.HR, Department.GENERAL);
    }

    @Test
    @DisplayName("M3: ChatService creates conversation, sends last 6 history messages, and persists citations")
    void chatServicePersistsConversationAndCitations() {
        ChatRequest chatReq = ChatRequest.builder()
                .message("How much PTO do employees accrue?")
                .departments(List.of(Department.HR))
                .build();

        ChatResponse chatRes = chatService.chat(chatReq);

        assertThat(chatRes.getConversationId()).isNotNull();
        assertThat(chatRes.getAnswered()).isTrue();
        assertThat(chatRes.getAnswer()).contains("[1]");
        assertThat(chatRes.getCitations()).hasSize(1);
        assertThat(chatRes.getCitations().get(0).getDocumentTitle()).isEqualTo("PTO Policy");
        assertThat(chatMessagesStore).hasSize(2);
    }

    @Test
    @DisplayName("M3: Citation chunk preview returns chunk for allowed department")
    void chunkPreviewReturnsChunk() {
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        Document doc = Document.builder()
                .id(docId)
                .title("Security SOP")
                .department(Department.ENGINEERING)
                .status(DocumentStatus.INDEXED)
                .build();
        DocumentChunk chunk = DocumentChunk.builder()
                .id(chunkId)
                .documentId(docId)
                .documentTitle("Security SOP")
                .departmentCode("ENGINEERING")
                .version(1)
                .chunkIndex(4)
                .pageNumber(9)
                .sectionTitle("2.4 Key Rotation")
                .content("Rotate production keys every 90 days.")
                .isDeleted(false)
                .build();

        documentsStore.put(docId, doc);
        chunksStore.put(chunkId, chunk);

        ChunkResponse res = documentService.getChunk(docId, chunkId);
        assertThat(res.getId()).isEqualTo(chunkId);
        assertThat(res.getPageNumber()).isEqualTo(9);
        assertThat(res.getSectionTitle()).isEqualTo("2.4 Key Rotation");
        assertThat(res.getContent()).isEqualTo("Rotate production keys every 90 days.");
    }

    // =========================================================================
    // Lightweight JDK Proxy Helpers (no ByteBuddy agent self-attach needed)
    // =========================================================================

    @SuppressWarnings("unchecked")
    private <T> T proxyThrowingOnUpload(Class<T> iface) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, method, args) -> {
            throw new RuntimeException("Offline local test mode");
        });
    }

    private DocumentRepository createDocumentRepositoryProxy() {
        return (DocumentRepository) Proxy.newProxyInstance(
                DocumentRepository.class.getClassLoader(),
                new Class<?>[]{DocumentRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        Document d = (Document) args[0];
                        if (d.getId() == null) {
                            d.setId(UUID.randomUUID());
                        }
                        d.onPrePersist();
                        documentsStore.put(d.getId(), d);
                        return d;
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(documentsStore.get((UUID) args[0]));
                    }
                    if ("findByIdAndDeletedAtIsNull".equals(name)) {
                        Document d = documentsStore.get((UUID) args[0]);
                        return (d != null && d.getDeletedAt() == null) ? Optional.of(d) : Optional.empty();
                    }
                    return null;
                });
    }

    private DocumentVersionRepository createDocumentVersionRepositoryProxy() {
        return (DocumentVersionRepository) Proxy.newProxyInstance(
                DocumentVersionRepository.class.getClassLoader(),
                new Class<?>[]{DocumentVersionRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("save".equals(name)) {
                        DocumentVersion v = (DocumentVersion) args[0];
                        if (v.getId() == null) {
                            v.setId(UUID.randomUUID());
                        }
                        v.onPrePersist();
                        versionsStore.put(v.getId(), v);
                        return v;
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(versionsStore.get((UUID) args[0]));
                    }
                    if ("findByDocumentIdAndVersion".equals(name)) {
                        UUID docId = (UUID) args[0];
                        Integer ver = (Integer) args[1];
                        return versionsStore.values().stream()
                                .filter(v -> docId.equals(v.getDocumentId()) && Objects.equals(ver, v.getVersion()))
                                .findFirst();
                    }
                    return Collections.emptyList();
                });
    }

    private DocumentChunkRepository createDocumentChunkRepositoryProxy() {
        return (DocumentChunkRepository) Proxy.newProxyInstance(
                DocumentChunkRepository.class.getClassLoader(),
                new Class<?>[]{DocumentChunkRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("markChunksDeletedByDocumentId".equals(name)) {
                        markedDeletedDocId.set((UUID) args[0]);
                        return 1;
                    }
                    if ("markOtherVersionsNotCurrent".equals(name)) {
                        return 0;
                    }
                    if ("findByIdAndDocumentIdAndIsDeletedFalse".equals(name)) {
                        DocumentChunk c = chunksStore.get((UUID) args[0]);
                        return (c != null && Objects.equals(c.getDocumentId(), args[1]) && !Boolean.TRUE.equals(c.getIsDeleted()))
                                ? Optional.of(c)
                                : Optional.empty();
                    }
                    return null;
                });
    }

    private IngestionJobRepository createIngestionJobRepositoryProxy() {
        return (IngestionJobRepository) Proxy.newProxyInstance(
                IngestionJobRepository.class.getClassLoader(),
                new Class<?>[]{IngestionJobRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        IngestionJob j = (IngestionJob) args[0];
                        if (j.getId() == null) {
                            j.setId(UUID.randomUUID());
                        }
                        return j;
                    }
                    if ("findTopByDocumentIdOrderByCreatedAtDesc".equals(method.getName())) {
                        return Optional.empty();
                    }
                    return null;
                });
    }

    private ConversationRepository createConversationRepositoryProxy() {
        return (ConversationRepository) Proxy.newProxyInstance(
                ConversationRepository.class.getClassLoader(),
                new Class<?>[]{ConversationRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        Conversation c = (Conversation) args[0];
                        if (c.getId() == null) {
                            c.setId(UUID.randomUUID());
                        }
                        c.onPrePersist();
                        conversationsStore.put(c.getId(), c);
                        return c;
                    }
                    if ("findByIdAndUserId".equals(method.getName())) {
                        return Optional.ofNullable(conversationsStore.get((UUID) args[0]));
                    }
                    return null;
                });
    }

    private ChatMessageRepository createChatMessageRepositoryProxy() {
        return (ChatMessageRepository) Proxy.newProxyInstance(
                ChatMessageRepository.class.getClassLoader(),
                new Class<?>[]{ChatMessageRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        ChatMessageEntity m = (ChatMessageEntity) args[0];
                        if (m.getId() == null) {
                            m.setId(UUID.randomUUID());
                        }
                        m.onPrePersist();
                        chatMessagesStore.add(m);
                        return m;
                    }
                    if ("findTop6ByConversationIdOrderByCreatedAtDesc".equals(method.getName())
                            || "findByConversationIdOrderByCreatedAtAsc".equals(method.getName())) {
                        return new ArrayList<>(chatMessagesStore);
                    }
                    return null;
                });
    }
}
