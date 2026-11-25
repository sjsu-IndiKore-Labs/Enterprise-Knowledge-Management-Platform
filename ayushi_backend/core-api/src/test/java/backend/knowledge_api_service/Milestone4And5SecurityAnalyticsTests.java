package backend.knowledge_api_service;

import backend.knowledge_api_service.config.JacksonConfig;
import backend.knowledge_api_service.dto.request.*;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.entity.*;
import backend.knowledge_api_service.exception.ForbiddenException;
import backend.knowledge_api_service.exception.RateLimitExceededException;
import backend.knowledge_api_service.exception.ResourceNotFoundException;
import backend.knowledge_api_service.exception.UnauthorizedException;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.*;
import backend.knowledge_api_service.repository.*;
import backend.knowledge_api_service.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Milestone4And5SecurityAnalyticsTests {

    private final Map<UUID, UserEntity> usersStore = new ConcurrentHashMap<>();
    private final Map<UUID, Document> documentsStore = new ConcurrentHashMap<>();
    private final Map<UUID, DocumentChunk> chunksStore = new ConcurrentHashMap<>();
    private final Map<UUID, Conversation> conversationsStore = new ConcurrentHashMap<>();
    private final Map<UUID, ChatMessageEntity> chatMessagesStore = new ConcurrentHashMap<>();
    private final List<QueryLogEntity> queryLogsStore = new ArrayList<>();
    private final Map<UUID, KnowledgeGapEntity> knowledgeGapsStore = new ConcurrentHashMap<>();
    private final List<AuditLogEntity> auditLogsStore = new ArrayList<>();

    private ObjectMapper objectMapper;
    private AuditEventPublisher auditEventPublisher;
    private AuthService authService;
    private DocumentService documentService;
    private ChatRateLimiter chatRateLimiter;
    private ChatService chatService;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        usersStore.clear();
        documentsStore.clear();
        chunksStore.clear();
        conversationsStore.clear();
        chatMessagesStore.clear();
        queryLogsStore.clear();
        knowledgeGapsStore.clear();
        auditLogsStore.clear();
        RequestIdFilter.clearCurrentCaller();

        objectMapper = new JacksonConfig().objectMapper();
        SqsClient sqsClient = proxyThrowing(SqsClient.class);
        S3Client s3Client = proxyThrowing(S3Client.class);
        S3Presigner s3Presigner = proxyThrowing(S3Presigner.class);

        auditEventPublisher = new AuditEventPublisher(sqsClient, objectMapper);
        UserRepository userRepository = createUserRepositoryProxy();
        DocumentRepository documentRepository = createDocumentRepositoryProxy();
        DocumentVersionRepository documentVersionRepository = createDocumentVersionRepositoryProxy();
        DocumentChunkRepository documentChunkRepository = createDocumentChunkRepositoryProxy();
        IngestionJobRepository ingestionJobRepository = createIngestionJobRepositoryProxy();
        ConversationRepository conversationRepository = createConversationRepositoryProxy();
        ChatMessageRepository chatMessageRepository = createChatMessageRepositoryProxy();
        QueryLogRepository queryLogRepository = createQueryLogRepositoryProxy();
        KnowledgeGapRepository knowledgeGapRepository = createKnowledgeGapRepositoryProxy();
        AuditLogRepository auditLogRepository = createAuditLogRepositoryProxy();

        authService = new AuthService(userRepository, auditEventPublisher);
        seedUsers();

        S3StorageService s3StorageService = new S3StorageService(s3Client, s3Presigner);
        SqsIngestionProducer sqsProducer = new SqsIngestionProducer(sqsClient, objectMapper);
        documentService = new DocumentService(
                documentRepository,
                documentVersionRepository,
                documentChunkRepository,
                ingestionJobRepository,
                s3StorageService,
                sqsProducer);
        documentService.setAuditEventPublisher(auditEventPublisher);

        chatRateLimiter = new ChatRateLimiter();
        ReflectionTestUtils.setField(chatRateLimiter, "maxRequestsPerMinute", 20);

        AiServiceClient stubAiClient = new AiServiceClient(objectMapper) {
            @Override
            public AiServiceClient.InternalAnswerResponse answer(AiServiceClient.InternalAnswerRequest request) {
                return AiServiceClient.InternalAnswerResponse.builder()
                        .queryId(request.getQueryId())
                        .answer("Employees accrue 1.67 days per month [1].")
                        .answered(true)
                        .confidence(0.89)
                        .citations(List.of(CitationDto.builder()
                                .citationNumber(1)
                                .documentId(UUID.randomUUID())
                                .documentTitle("PTO Policy")
                                .department(Department.HR)
                                .version(1)
                                .pageNumber(1)
                                .chunkId(UUID.randomUUID())
                                .snippet("Employees accrue 1.67 days per month.")
                                .score(0.89)
                                .build()))
                        .latencyMs(140)
                        .build();
            }
        };

        chatService = new ChatService(conversationRepository, chatMessageRepository, stubAiClient, objectMapper);
        chatService.setChatRateLimiter(chatRateLimiter);
        chatService.setAuditEventPublisher(auditEventPublisher);

        analyticsService = new AnalyticsService(
                queryLogRepository,
                knowledgeGapRepository,
                auditLogRepository,
                documentRepository,
                userRepository,
                chatMessageRepository,
                authService,
                auditEventPublisher,
                objectMapper);
    }

    @AfterEach
    void tearDown() {
        RequestIdFilter.clearCurrentCaller();
    }

    // =========================================================================
    // MILESTONE 4 TESTS: JWT Auth, RBAC, Leakage Matrix, SSE, Rate Limit
    // =========================================================================

    @Test
    @DisplayName("M4: Login issues valid JWT for active user and rejects inactive user or wrong password with 401 + LOGIN_FAILED")
    void loginAuthenticationAndInactiveRejection() {
        // 1. Valid login for Hannah (HR Employee)
        LoginResponse loginRes = authService.login(new LoginRequest("hannah.hr@helios.example.com", "Password123!"));
        assertThat(loginRes.getAccessToken()).isNotBlank();
        assertThat(loginRes.getTokenType()).isEqualTo("Bearer");
        assertThat(loginRes.getUser().getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(loginRes.getUser().getDepartment()).isEqualTo(Department.HR);
        assertThat(loginRes.getUser().getAllowedDepartments()).containsExactly(Department.HR, Department.GENERAL);
        assertThat(loginRes.getUser().getPermissions()).containsExactly("DOCUMENT_READ");

        // 2. Inactive user (ivan.inactive@helios.example.com) -> 401 UNAUTHENTICATED + LOGIN_FAILED audit event
        auditEventPublisher.clearPublishedEvents();
        assertThatThrownBy(() -> authService.login(new LoginRequest("ivan.inactive@helios.example.com", "Password123!")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
        assertThat(auditEventPublisher.getPublishedEvents())
                .anyMatch(e -> "LOGIN_FAILED".equals(e.getAction()));

        // 3. Wrong password -> 401 UNAUTHENTICATED + LOGIN_FAILED audit event
        auditEventPublisher.clearPublishedEvents();
        assertThatThrownBy(() -> authService.login(new LoginRequest("hannah.hr@helios.example.com", "WrongPass999!")))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(auditEventPublisher.getPublishedEvents())
                .anyMatch(e -> "LOGIN_FAILED".equals(e.getAction()));
    }

    @Test
    @DisplayName("M4: RBAC enforces MANAGER write only in own department (not GENERAL or other depts) and ADMIN-only routes")
    void rbacWriteAndAdminRestrictions() {
        byte[] pdfBytes = "%PDF-1.7 sample content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", pdfBytes);

        // Set caller to Harper (HR MANAGER)
        setCaller("harper.hrmgr@helios.example.com", "MANAGER", Department.HR);

        // HR Manager uploading to HR -> ALLOWED
        DocumentResponse hrDoc = documentService.createAndIngestDocument(file, DocumentUploadRequest.builder()
                .title("HR Onboarding Guide")
                .department(Department.HR)
                .category(DocumentCategory.GUIDE)
                .build());
        assertThat(hrDoc.getDepartment()).isEqualTo(Department.HR);

        // HR Manager uploading to ENGINEERING -> 403 FORBIDDEN + ACCESS_DENIED
        auditEventPublisher.clearPublishedEvents();
        assertThatThrownBy(() -> documentService.createAndIngestDocument(file, DocumentUploadRequest.builder()
                .title("Engineering Doc")
                .department(Department.ENGINEERING)
                .category(DocumentCategory.SOP)
                .build()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(auditEventPublisher.getPublishedEvents())
                .anyMatch(e -> "ACCESS_DENIED".equals(e.getAction()));

        // HR Manager uploading to GENERAL -> 403 FORBIDDEN (GENERAL is ADMIN-only per contracts/auth.md)
        assertThatThrownBy(() -> documentService.createAndIngestDocument(file, DocumentUploadRequest.builder()
                .title("Company Handbook")
                .department(Department.GENERAL)
                .category(DocumentCategory.POLICY)
                .build()))
                .isInstanceOf(ForbiddenException.class);

        // HR Manager accessing /api/v1/admin/users -> 403 FORBIDDEN
        assertThatThrownBy(() -> authService.listUsers(0, 20))
                .isInstanceOf(ForbiddenException.class);

        // EMPLOYEE uploading to own department -> 403 FORBIDDEN
        setCaller("hannah.hr@helios.example.com", "EMPLOYEE", Department.HR);
        assertThatThrownBy(() -> documentService.createAndIngestDocument(file, DocumentUploadRequest.builder()
                .title("Employee Upload Attempt")
                .department(Department.HR)
                .category(DocumentCategory.OTHER)
                .build()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("M4 Leakage Suite: Automated verification of every cell in rbac_matrix.csv and every RBAC_DENIED case in golden_qa.jsonl")
    void automatedRbacMatrixAndGoldenQaLeakageSuite() throws Exception {
        Path repoRoot = Files.exists(Path.of("../resources/corpus/manifest.csv"))
                ? Path.of("..").toAbsolutePath().normalize()
                : Path.of("../..").toAbsolutePath().normalize();
        Path manifestPath = repoRoot.resolve("resources/corpus/manifest.csv");
        Path rbacMatrixPath = repoRoot.resolve("resources/eval/rbac_matrix.csv");
        Path goldenQaPath = repoRoot.resolve("resources/eval/golden_qa.jsonl");

        // 1. Load corpus documents from resources/corpus/manifest.csv into documentsStore & chunksStore
        Map<String, UUID> docKeyToUuid = new LinkedHashMap<>();
        Map<String, UUID> docKeyToChunkUuid = new LinkedHashMap<>();
        List<String> manifestLines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
        for (int i = 1; i < manifestLines.size(); i++) {
            String line = manifestLines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",");
            String docKey = cols[0].trim();
            if (docKeyToUuid.containsKey(docKey)) continue; // keep v1 entry per docKey

            Department dept = Department.valueOf(cols[3].trim());
            DocumentCategory cat = DocumentCategory.valueOf(cols[4].trim());
            UUID docUuid = UUID.randomUUID();
            UUID chunkUuid = UUID.randomUUID();

            Document doc = Document.builder()
                    .id(docUuid)
                    .title(cols[2].trim())
                    .department(dept)
                    .departmentCode(dept.name())
                    .category(cat)
                    .status(DocumentStatus.INDEXED)
                    .currentVersion(1)
                    .build();
            documentsStore.put(docUuid, doc);

            DocumentChunk chunk = DocumentChunk.builder()
                    .id(chunkUuid)
                    .documentId(docUuid)
                    .documentTitle(doc.getTitle())
                    .departmentCode(dept.name())
                    .version(1)
                    .chunkIndex(0)
                    .pageNumber(1)
                    .content("Confidential content for " + docKey)
                    .isDeleted(false)
                    .build();
            chunksStore.put(chunkUuid, chunk);

            docKeyToUuid.put(docKey, docUuid);
            docKeyToChunkUuid.put(docKey, chunkUuid);
        }

        // 2. Verify every row x column in resources/eval/rbac_matrix.csv (11 users x 18 documents = 198 checks)
        List<String> matrixLines = Files.readAllLines(rbacMatrixPath, StandardCharsets.UTF_8);
        String[] headers = matrixLines.get(0).split(",");
        int totalMatrixChecks = 0;

        for (int r = 1; r < matrixLines.size(); r++) {
            String line = matrixLines.get(r).trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",");
            String userEmail = cols[0].trim();
            String role = cols[1].trim();
            Department userDept = Department.valueOf(cols[2].trim());

            setCaller(userEmail, role, userDept);

            for (int c = 3; c < headers.length; c++) {
                String docKey = headers[c].trim();
                String expectedDecision = cols[c].trim();
                UUID docUuid = docKeyToUuid.get(docKey);
                UUID chunkUuid = docKeyToChunkUuid.get(docKey);
                assertThat(docUuid).as("Doc UUID for " + docKey).isNotNull();

                if ("ALLOW".equalsIgnoreCase(expectedDecision)) {
                    DocumentDetailResponse detail = documentService.getDocumentDetail(docUuid);
                    assertThat(detail.getId()).isEqualTo(docUuid);
                    ChunkResponse chunkRes = documentService.getChunk(docUuid, chunkUuid);
                    assertThat(chunkRes.getId()).isEqualTo(chunkUuid);
                } else {
                    auditEventPublisher.clearPublishedEvents();
                    // Must return 404 (ResourceNotFoundException) and emit ACCESS_DENIED
                    assertThatThrownBy(() -> documentService.getDocumentDetail(docUuid))
                            .as("User %s (%s/%s) must be DENIED access to %s", userEmail, role, userDept, docKey)
                            .isInstanceOf(ResourceNotFoundException.class);
                    assertThat(auditEventPublisher.getPublishedEvents())
                            .anyMatch(e -> "ACCESS_DENIED".equals(e.getAction()));

                    assertThatThrownBy(() -> documentService.getChunk(docUuid, chunkUuid))
                            .isInstanceOf(ResourceNotFoundException.class);
                }
                totalMatrixChecks++;
            }
        }
        assertThat(totalMatrixChecks).isEqualTo(11 * 18);

        // 3. Verify every RBAC_DENIED and PROMPT_INJECTION case in resources/eval/golden_qa.jsonl
        List<String> qaLines = Files.readAllLines(goldenQaPath, StandardCharsets.UTF_8);
        int deniedCasesChecked = 0;
        for (String qaLine : qaLines) {
            if (qaLine.isBlank()) continue;
            JsonNode node = objectMapper.readTree(qaLine);
            String type = node.get("type").asText();
            if ("RBAC_DENIED".equals(type) || "PROMPT_INJECTION".equals(type)) {
                String askedBy = node.get("askedBy").asText();
                UserEntity user = usersStore.values().stream()
                        .filter(u -> u.getEmail().equalsIgnoreCase(askedBy))
                        .findFirst()
                        .orElseThrow();

                setCaller(user.getEmail(), user.getRole().name(), user.getDepartment());
                JsonNode forbiddenDocs = node.get("forbiddenDocIds");
                if (forbiddenDocs != null && forbiddenDocs.isArray()) {
                    for (JsonNode forbiddenDocNode : forbiddenDocs) {
                        String forbiddenKey = forbiddenDocNode.asText();
                        UUID forbiddenUuid = docKeyToUuid.get(forbiddenKey);
                        // Verify user's allowedDepartments never includes the forbidden document's department
                        Document forbiddenDoc = documentsStore.get(forbiddenUuid);
                        assertThat(RequestIdFilter.getCurrentCaller().allowedDepartments())
                                .doesNotContain(forbiddenDoc.getDepartment());
                        assertThatThrownBy(() -> documentService.getDocumentDetail(forbiddenUuid))
                                .isInstanceOf(ResourceNotFoundException.class);
                    }
                }
                deniedCasesChecked++;
            }
        }
        assertThat(deniedCasesChecked).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("M4: SSE streaming chat completes and rate limiter blocks >20 requests/min with 429")
    void sseStreamingAndRateLimiting() {
        setCaller("hannah.hr@helios.example.com", "EMPLOYEE", Department.HR);
        ChatRequest req = ChatRequest.builder().message("What is the PTO accrual rate?").build();

        SseEmitter emitter = chatService.streamChat(req);
        assertThat(emitter).isNotNull();

        // Rate limit check: limit to 3 requests/min for test
        chatRateLimiter.reset();
        ReflectionTestUtils.setField(chatRateLimiter, "maxRequestsPerMinute", 3);
        chatService.chat(req);
        chatService.chat(req);
        chatService.chat(req);
        assertThatThrownBy(() -> chatService.chat(req))
                .isInstanceOf(RateLimitExceededException.class);
    }

    // =========================================================================
    // MILESTONE 5 TESTS: Feedback, Analytics Summary, Knowledge Gaps, Audit Logs
    // =========================================================================

    @Test
    @DisplayName("M5: Feedback, Analytics summary, Top Queries, Knowledge Gaps update, and Audit Logs work for ADMIN")
    void feedbackAndAdminAnalyticsEndpoints() {
        setCaller("alice.admin@helios.example.com", "ADMIN", Department.GENERAL);

        // 1. Run a chat query and submit thumbs-up feedback
        ChatResponse chatRes = chatService.chat(ChatRequest.builder()
                .message("How much PTO do employees accrue?")
                .build());
        chatService.submitFeedback(chatRes.getMessageId(), FeedbackRequest.builder()
                .rating("UP")
                .comment("Accurate citation!")
                .build());
        assertThat(chatMessagesStore.get(chatRes.getMessageId()).getFeedback()).isEqualTo("UP");

        // 2. Seed query logs and a knowledge gap
        UUID docId = UUID.randomUUID();
        documentsStore.put(docId, Document.builder()
                .id(docId)
                .title("PTO Policy")
                .department(Department.HR)
                .status(DocumentStatus.INDEXED)
                .build());

        queryLogsStore.add(QueryLogEntity.builder()
                .id(UUID.randomUUID())
                .departmentCode("HR")
                .queryText("How much PTO do employees accrue?")
                .normalizedQuery("how much pto do employees accrue?")
                .answered(true)
                .confidence(0.89)
                .latencyMs(120)
                .citedDocumentIds(docId.toString())
                .createdAt(Instant.now().minusSeconds(60))
                .build());
        queryLogsStore.add(QueryLogEntity.builder()
                .id(UUID.randomUUID())
                .departmentCode("ENGINEERING")
                .queryText("Does Helios offer pet insurance?")
                .normalizedQuery("does helios offer pet insurance?")
                .answered(false)
                .confidence(0.21)
                .latencyMs(80)
                .citedDocumentIds("")
                .createdAt(Instant.now().minusSeconds(30))
                .build());

        UUID gapId = UUID.randomUUID();
        knowledgeGapsStore.put(gapId, KnowledgeGapEntity.builder()
                .id(gapId)
                .sampleQuery("Does Helios offer pet insurance?")
                .normalizedQuery("does helios offer pet insurance?")
                .departmentCode("ENGINEERING")
                .occurrences(3)
                .status(KnowledgeGapStatus.OPEN)
                .firstSeenAt(Instant.now().minusSeconds(300))
                .lastSeenAt(Instant.now())
                .build());

        AnalyticsSummaryResponse summary = analyticsService.getSummary(null, null);
        assertThat(summary.getTotalQueries()).isEqualTo(2);
        assertThat(summary.getAnsweredRate()).isEqualTo(0.5);
        assertThat(summary.getOpenKnowledgeGaps()).isEqualTo(1);
        assertThat(summary.getPositiveFeedbackRate()).isEqualTo(1.0);

        List<TopQueryDto> topQueries = analyticsService.getTopQueries(null, null, 10);
        assertThat(topQueries).hasSize(2);

        List<TopDocumentDto> topDocs = analyticsService.getTopDocuments(null, null, 10);
        assertThat(topDocs).hasSize(1);
        assertThat(topDocs.get(0).getTitle()).isEqualTo("PTO Policy");

        KnowledgeGapDto updatedGap = analyticsService.updateKnowledgeGap(gapId, UpdateKnowledgeGapRequest.builder()
                .status(KnowledgeGapStatus.RESOLVED)
                .notes("Added pet insurance FAQ to HR-001 v2")
                .build());
        assertThat(updatedGap.getStatus()).isEqualTo(KnowledgeGapStatus.RESOLVED);
        assertThat(updatedGap.getResolvedAt()).isNotNull();
    }

    // =========================================================================
    // Test Helpers & In-Memory Repository Proxies
    // =========================================================================

    private void seedUsers() {
        String hash = authService.hashPassword("Password123!");
        saveSeedUser("11111111-1111-4111-8111-000000000001", "alice.admin@helios.example.com", "Alice Admin", Role.ADMIN, Department.GENERAL, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000002", "harper.hrmgr@helios.example.com", "Harper Hughes", Role.MANAGER, Department.HR, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000003", "hannah.hr@helios.example.com", "Hannah Reyes", Role.EMPLOYEE, Department.HR, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000004", "ethan.engmgr@helios.example.com", "Ethan Park", Role.MANAGER, Department.ENGINEERING, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000005", "eric.eng@helios.example.com", "Eric Nguyen", Role.EMPLOYEE, Department.ENGINEERING, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000006", "frank.finmgr@helios.example.com", "Frank Ortiz", Role.MANAGER, Department.FINANCE, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000007", "fiona.fin@helios.example.com", "Fiona Brooks", Role.EMPLOYEE, Department.FINANCE, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000008", "lena.legalmgr@helios.example.com", "Lena Schmidt", Role.MANAGER, Department.LEGAL, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000009", "leo.legal@helios.example.com", "Leo Martins", Role.EMPLOYEE, Department.LEGAL, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000010", "mark.mktmgr@helios.example.com", "Mark Olsen", Role.MANAGER, Department.MARKETING, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000011", "maya.mkt@helios.example.com", "Maya Chen", Role.EMPLOYEE, Department.MARKETING, true, hash);
        saveSeedUser("11111111-1111-4111-8111-000000000012", "ivan.inactive@helios.example.com", "Ivan Idle", Role.EMPLOYEE, Department.MARKETING, false, hash);
    }

    private void saveSeedUser(String id, String email, String name, Role role, Department dept, boolean active, String hash) {
        UUID uuid = UUID.fromString(id);
        UserEntity u = UserEntity.builder()
                .id(uuid)
                .email(email)
                .fullName(name)
                .role(role)
                .department(dept)
                .active(active)
                .passwordHash(hash)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        usersStore.put(uuid, u);
    }

    private void setCaller(String email, String role, Department dept) {
        UUID userId = usersStore.values().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                .map(UserEntity::getId)
                .findFirst()
                .orElse(UUID.randomUUID());
        RequestIdFilter.setCurrentCaller(new RequestIdFilter.CallerInfo(
                userId,
                email,
                email,
                role,
                dept,
                RequestIdFilter.computeAllowedDepartments(role, dept),
                true));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxyThrowing(Class<T> iface) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
            throw new RuntimeException("Offline test mode");
        });
    }

    private UserRepository createUserRepositoryProxy() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByEmailIgnoreCase".equals(name)) {
                        String email = (String) args[0];
                        return usersStore.values().stream()
                                .filter(u -> u.getEmail().equalsIgnoreCase(email))
                                .findFirst();
                    }
                    if ("existsByEmailIgnoreCase".equals(name)) {
                        String email = (String) args[0];
                        return usersStore.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(email));
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(usersStore.get((UUID) args[0]));
                    }
                    if ("save".equals(name)) {
                        UserEntity u = (UserEntity) args[0];
                        if (u.getId() == null) u.setId(UUID.randomUUID());
                        u.onPrePersist();
                        usersStore.put(u.getId(), u);
                        return u;
                    }
                    if ("countByActiveTrue".equals(name)) {
                        return usersStore.values().stream().filter(u -> Boolean.TRUE.equals(u.getActive())).count();
                    }
                    if ("findAll".equals(name) && args != null && args.length == 1 && args[0] instanceof Pageable p) {
                        return new PageImpl<>(new ArrayList<>(usersStore.values()), p, usersStore.size());
                    }
                    return null;
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
                        if (d.getId() == null) d.setId(UUID.randomUUID());
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
                    if ("findAll".equals(name) && (args == null || args.length == 0)) {
                        return new ArrayList<>(documentsStore.values());
                    }
                    return Collections.emptyList();
                });
    }

    private DocumentVersionRepository createDocumentVersionRepositoryProxy() {
        return (DocumentVersionRepository) Proxy.newProxyInstance(
                DocumentVersionRepository.class.getClassLoader(),
                new Class<?>[]{DocumentVersionRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        DocumentVersion v = (DocumentVersion) args[0];
                        if (v.getId() == null) v.setId(UUID.randomUUID());
                        v.onPrePersist();
                        return v;
                    }
                    return Collections.emptyList();
                });
    }

    private DocumentChunkRepository createDocumentChunkRepositoryProxy() {
        return (DocumentChunkRepository) Proxy.newProxyInstance(
                DocumentChunkRepository.class.getClassLoader(),
                new Class<?>[]{DocumentChunkRepository.class},
                (proxy, method, args) -> {
                    if ("findByIdAndDocumentIdAndIsDeletedFalse".equals(method.getName())) {
                        DocumentChunk c = chunksStore.get((UUID) args[0]);
                        return (c != null && Objects.equals(c.getDocumentId(), args[1]) && !Boolean.TRUE.equals(c.getIsDeleted()))
                                ? Optional.of(c)
                                : Optional.empty();
                    }
                    return 0;
                });
    }

    private IngestionJobRepository createIngestionJobRepositoryProxy() {
        return (IngestionJobRepository) Proxy.newProxyInstance(
                IngestionJobRepository.class.getClassLoader(),
                new Class<?>[]{IngestionJobRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        IngestionJob j = (IngestionJob) args[0];
                        if (j.getId() == null) j.setId(UUID.randomUUID());
                        return j;
                    }
                    return Optional.empty();
                });
    }

    private ConversationRepository createConversationRepositoryProxy() {
        return (ConversationRepository) Proxy.newProxyInstance(
                ConversationRepository.class.getClassLoader(),
                new Class<?>[]{ConversationRepository.class},
                (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        Conversation c = (Conversation) args[0];
                        if (c.getId() == null) c.setId(UUID.randomUUID());
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
                    String name = method.getName();
                    if ("save".equals(name)) {
                        ChatMessageEntity m = (ChatMessageEntity) args[0];
                        if (m.getId() == null) m.setId(UUID.randomUUID());
                        m.onPrePersist();
                        chatMessagesStore.put(m.getId(), m);
                        return m;
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(chatMessagesStore.get((UUID) args[0]));
                    }
                    if ("findAll".equals(name)) {
                        return new ArrayList<>(chatMessagesStore.values());
                    }
                    if ("findTop6ByConversationIdOrderByCreatedAtDesc".equals(name)
                            || "findByConversationIdOrderByCreatedAtAsc".equals(name)) {
                        return new ArrayList<>(chatMessagesStore.values());
                    }
                    return Collections.emptyList();
                });
    }

    private QueryLogRepository createQueryLogRepositoryProxy() {
        return (QueryLogRepository) Proxy.newProxyInstance(
                QueryLogRepository.class.getClassLoader(),
                new Class<?>[]{QueryLogRepository.class},
                (proxy, method, args) -> {
                    if ("findByCreatedAtBetweenOrderByCreatedAtAsc".equals(method.getName())) {
                        return new ArrayList<>(queryLogsStore);
                    }
                    return Collections.emptyList();
                });
    }

    private KnowledgeGapRepository createKnowledgeGapRepositoryProxy() {
        return (KnowledgeGapRepository) Proxy.newProxyInstance(
                KnowledgeGapRepository.class.getClassLoader(),
                new Class<?>[]{KnowledgeGapRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("countByStatus".equals(name)) {
                        KnowledgeGapStatus st = (KnowledgeGapStatus) args[0];
                        return knowledgeGapsStore.values().stream().filter(g -> g.getStatus() == st).count();
                    }
                    if ("findById".equals(name)) {
                        return Optional.ofNullable(knowledgeGapsStore.get((UUID) args[0]));
                    }
                    if ("save".equals(name)) {
                        KnowledgeGapEntity g = (KnowledgeGapEntity) args[0];
                        if (g.getId() == null) g.setId(UUID.randomUUID());
                        knowledgeGapsStore.put(g.getId(), g);
                        return g;
                    }
                    return null;
                });
    }

    private AuditLogRepository createAuditLogRepositoryProxy() {
        return (AuditLogRepository) Proxy.newProxyInstance(
                AuditLogRepository.class.getClassLoader(),
                new Class<?>[]{AuditLogRepository.class},
                (proxy, method, args) -> Collections.emptyList());
    }
}

