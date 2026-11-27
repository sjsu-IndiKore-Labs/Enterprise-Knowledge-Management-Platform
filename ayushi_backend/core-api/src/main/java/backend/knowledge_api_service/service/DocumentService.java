package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.request.DocumentUploadRequest;
import backend.knowledge_api_service.dto.request.UpdateDocumentRequest;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.entity.Document;
import backend.knowledge_api_service.entity.DocumentChunk;
import backend.knowledge_api_service.entity.DocumentVersion;
import backend.knowledge_api_service.entity.IngestionJob;
import backend.knowledge_api_service.exception.ForbiddenException;
import backend.knowledge_api_service.exception.PayloadTooLargeException;
import backend.knowledge_api_service.exception.ResourceNotFoundException;
import backend.knowledge_api_service.exception.UnsupportedMediaTypeException;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.*;
import backend.knowledge_api_service.repository.DocumentChunkRepository;
import backend.knowledge_api_service.repository.DocumentRepository;
import backend.knowledge_api_service.repository.DocumentVersionRepository;
import backend.knowledge_api_service.repository.IngestionJobRepository;
import com.helios.ekm.common.event.DocumentIngestRequestedEvent;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentService {

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final S3StorageService s3StorageService;
    private final SqsIngestionProducer sqsIngestionProducer;
    private AuditEventPublisher auditEventPublisher;

    @Value("${ekm.upload.max-mb:25}")
    private long maxUploadMb = 25;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentChunkRepository documentChunkRepository,
            IngestionJobRepository ingestionJobRepository,
            S3StorageService s3StorageService,
            SqsIngestionProducer sqsIngestionProducer) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.s3StorageService = s3StorageService;
        this.sqsIngestionProducer = sqsIngestionProducer;
    }

    @Autowired(required = false)
    public void setAuditEventPublisher(AuditEventPublisher auditEventPublisher) {
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public DocumentResponse createAndIngestDocument(MultipartFile file, DocumentUploadRequest metadata) {
        enforceWritePermission(metadata.getDepartment(), null);

        byte[] fileBytes = readAndValidateFileBytes(file);
        String resolvedContentType = detectContentTypeByMagicBytes(file, fileBytes);
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String checksumSha256 = s3StorageService.computeSha256Hex(fileBytes);
        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();

        DocumentCategory category = metadata.getCategory() != null ? metadata.getCategory() : DocumentCategory.OTHER;
        ClassificationLevel classification = metadata.getClassification() != null
                ? metadata.getClassification()
                : ClassificationLevel.INTERNAL;

        Document document = Document.builder()
                .title(metadata.getTitle().trim())
                .fileName(originalFilename)
                .fileType(resolvedContentType)
                .fileSize((long) fileBytes.length)
                .department(metadata.getDepartment())
                .departmentCode(metadata.getDepartment().name())
                .category(category)
                .author(metadata.getAuthor())
                .tags(metadata.getTags() != null ? metadata.getTags().trim() : "")
                .classification(classification)
                .version(1)
                .currentVersion(1)
                .status(DocumentStatus.QUEUED)
                .createdBy(caller.userId())
                .build();

        Document savedDoc = documentRepository.save(document);

        String bucket = s3StorageService.getDocumentsBucket();
        String s3Key = s3StorageService.buildS3Key(
                savedDoc.getDepartment().name(), savedDoc.getId(), 1, originalFilename);
        savedDoc.setS3Key(s3Key);
        savedDoc = documentRepository.save(savedDoc);

        s3StorageService.uploadFile(bucket, s3Key, fileBytes, resolvedContentType, checksumSha256);

        DocumentVersion version = DocumentVersion.builder()
                .documentId(savedDoc.getId())
                .version(1)
                .s3Bucket(bucket)
                .s3Key(s3Key)
                .originalFilename(originalFilename)
                .contentType(resolvedContentType)
                .sizeBytes((long) fileBytes.length)
                .checksumSha256(checksumSha256)
                .status(DocumentStatus.QUEUED)
                .uploadedBy(caller.userId())
                .build();
        DocumentVersion savedVersion = documentVersionRepository.save(version);

        IngestionJob job = IngestionJob.builder()
                .document(savedDoc)
                .status(DocumentStatus.QUEUED)
                .progressPercent(0)
                .build();
        ingestionJobRepository.save(job);

        DocumentIngestRequestedEvent event = DocumentIngestRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("DOCUMENT_INGEST_REQUESTED")
                .occurredAt(Instant.now())
                .traceId(RequestIdFilter.getCurrentTraceId())
                .documentId(savedDoc.getId())
                .versionId(savedVersion.getId())
                .version(1)
                .previousVersionId(null)
                .s3Bucket(bucket)
                .s3Key(s3Key)
                .contentType(resolvedContentType)
                .originalFilename(originalFilename)
                .sizeBytes((long) fileBytes.length)
                .title(savedDoc.getTitle())
                .department(savedDoc.getDepartment().name())
                .category(savedDoc.getCategory().name())
                .tags(savedDoc.getTagList())
                .author(savedDoc.getAuthor())
                .uploadedBy(caller.userId())
                .build();

        sqsIngestionProducer.publishIngestRequested(event);
        publishAudit(AuditAction.DOCUMENT_UPLOADED, "DOCUMENT", savedDoc.getId().toString(),
                Map.of("title", savedDoc.getTitle(), "department", savedDoc.getDepartment().name(), "version", 1));

        return toDocumentResponse(savedDoc);
    }

    @Transactional
    public DocumentUploadResponse processUpload(MultipartFile file, DocumentUploadRequest metadata) {
        DocumentResponse doc = createAndIngestDocument(file, metadata);
        IngestionJob job = ingestionJobRepository.findTopByDocumentIdOrderByCreatedAtDesc(doc.getId())
                .orElse(null);

        return DocumentUploadResponse.builder()
                .documentId(doc.getId())
                .jobId(job != null ? job.getId() : UUID.randomUUID())
                .title(doc.getTitle())
                .fileName(doc.getOriginalFilename())
                .status(doc.getStatus())
                .message("Document uploaded successfully and queued for background indexing.")
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentPageResponse listDocumentsPage(
            int page,
            int size,
            String sortParam,
            Department departmentFilter,
            DocumentCategory categoryFilter,
            DocumentStatus statusFilter,
            String queryText) {

        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        List<Department> allowedDepts = caller.allowedDepartments();

        List<Department> effectiveDepartments;
        if (departmentFilter != null) {
            if (allowedDepts.contains(departmentFilter)) {
                effectiveDepartments = List.of(departmentFilter);
            } else {
                return DocumentPageResponse.builder()
                        .page(page)
                        .size(size)
                        .totalItems(0)
                        .totalPages(0)
                        .items(Collections.emptyList())
                        .build();
            }
        } else {
            effectiveDepartments = allowedDepts;
        }

        Sort sort = parseSort(sortParam);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), sort);

        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(root.get("department").in(effectiveDepartments));

            if (categoryFilter != null) {
                predicates.add(cb.equal(root.get("category"), categoryFilter));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            if (queryText != null && !queryText.isBlank()) {
                String pattern = "%" + queryText.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate tagsMatch = cb.like(cb.lower(root.get("tags")), pattern);
                predicates.add(cb.or(titleMatch, tagsMatch));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Document> docPage = documentRepository.findAll(spec, pageable);
        List<DocumentResponse> items = docPage.getContent().stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());

        return DocumentPageResponse.builder()
                .page(docPage.getNumber())
                .size(docPage.getSize())
                .totalItems(docPage.getTotalElements())
                .totalPages(docPage.getTotalPages())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments() {
        return listDocumentsPage(0, 100, "updatedAt,desc", null, null, null, null).getItems();
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocumentDetail(UUID documentId) {
        Document doc = findAuthorizedDocumentOrThrow(documentId);
        List<DocumentVersionDto> versions = documentVersionRepository
                .findByDocumentIdOrderByVersionDesc(documentId)
                .stream()
                .map(this::toVersionDto)
                .collect(Collectors.toList());

        publishAudit(AuditAction.DOCUMENT_VIEWED, "DOCUMENT", doc.getId().toString(),
                Map.of("department", doc.getDepartment().name()));

        return DocumentDetailResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .department(doc.getDepartment())
                .category(doc.getCategory())
                .author(doc.getAuthor())
                .tags(doc.getTagList())
                .currentVersion(doc.getCurrentVersion())
                .status(doc.getStatus())
                .originalFilename(doc.getFileName())
                .contentType(doc.getFileType())
                .pageCount(doc.getPageCount())
                .createdBy(doc.getCreatedBy())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .versions(versions)
                .build();
    }

    @Transactional
    public DocumentResponse updateDocumentMetadata(UUID documentId, UpdateDocumentRequest request) {
        Document doc = findAuthorizedDocumentOrThrow(documentId);
        enforceWritePermission(doc.getDepartment(), doc.getId().toString());

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            doc.setTitle(request.getTitle().trim());
        }
        if (request.getCategory() != null) {
            doc.setCategory(request.getCategory());
        }
        if (request.getAuthor() != null) {
            doc.setAuthor(request.getAuthor().trim());
        }
        if (request.getTags() != null) {
            doc.setTagList(request.getTags());
        }
        Document saved = documentRepository.save(doc);
        publishAudit(AuditAction.DOCUMENT_UPDATED, "DOCUMENT", saved.getId().toString(),
                Map.of("title", saved.getTitle(), "department", saved.getDepartment().name()));
        return toDocumentResponse(saved);
    }

    @Transactional
    public void softDeleteDocument(UUID documentId) {
        Document doc = findAuthorizedDocumentOrThrow(documentId);
        enforceWritePermission(doc.getDepartment(), doc.getId().toString());

        doc.setDeletedAt(Instant.now());
        documentRepository.save(doc);
        documentChunkRepository.markChunksDeletedByDocumentId(documentId);
        publishAudit(AuditAction.DOCUMENT_DELETED, "DOCUMENT", documentId.toString(),
                Map.of("department", doc.getDepartment().name()));
        log.info("Soft-deleted documentId={} and marked its chunks deleted", documentId);
    }

    @Transactional
    public DocumentVersionDto uploadNewVersion(UUID documentId, MultipartFile file, String changeNote) {
        Document doc = findAuthorizedDocumentOrThrow(documentId);
        enforceWritePermission(doc.getDepartment(), doc.getId().toString());

        byte[] fileBytes = readAndValidateFileBytes(file);
        String resolvedContentType = detectContentTypeByMagicBytes(file, fileBytes);
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : doc.getFileName();
        String checksumSha256 = s3StorageService.computeSha256Hex(fileBytes);
        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();

        List<DocumentVersion> existingVersions = documentVersionRepository.findByDocumentIdOrderByVersionDesc(documentId);
        int nextVersion = existingVersions.isEmpty()
                ? (doc.getCurrentVersion() != null ? doc.getCurrentVersion() + 1 : 2)
                : existingVersions.get(0).getVersion() + 1;

        UUID previousVersionId = existingVersions.stream()
                .filter(v -> v.getStatus() == DocumentStatus.INDEXED)
                .map(DocumentVersion::getId)
                .findFirst()
                .orElseGet(() -> existingVersions.isEmpty() ? null : existingVersions.get(0).getId());

        String bucket = s3StorageService.getDocumentsBucket();
        String s3Key = s3StorageService.buildS3Key(doc.getDepartment().name(), doc.getId(), nextVersion, originalFilename);

        s3StorageService.uploadFile(bucket, s3Key, fileBytes, resolvedContentType, checksumSha256);

        DocumentVersion newVersion = DocumentVersion.builder()
                .documentId(doc.getId())
                .version(nextVersion)
                .s3Bucket(bucket)
                .s3Key(s3Key)
                .originalFilename(originalFilename)
                .contentType(resolvedContentType)
                .sizeBytes((long) fileBytes.length)
                .checksumSha256(checksumSha256)
                .status(DocumentStatus.QUEUED)
                .changeNote(changeNote)
                .uploadedBy(caller.userId())
                .build();
        DocumentVersion savedVersion = documentVersionRepository.save(newVersion);

        doc.setFileName(originalFilename);
        doc.setFileType(resolvedContentType);
        doc.setFileSize((long) fileBytes.length);
        doc.setS3Key(s3Key);
        doc.setStatus(DocumentStatus.QUEUED);
        documentRepository.save(doc);

        DocumentIngestRequestedEvent event = DocumentIngestRequestedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("DOCUMENT_INGEST_REQUESTED")
                .occurredAt(Instant.now())
                .traceId(RequestIdFilter.getCurrentTraceId())
                .documentId(doc.getId())
                .versionId(savedVersion.getId())
                .version(nextVersion)
                .previousVersionId(previousVersionId)
                .s3Bucket(bucket)
                .s3Key(s3Key)
                .contentType(resolvedContentType)
                .originalFilename(originalFilename)
                .sizeBytes((long) fileBytes.length)
                .title(doc.getTitle())
                .department(doc.getDepartment().name())
                .category(doc.getCategory().name())
                .tags(doc.getTagList())
                .author(doc.getAuthor())
                .uploadedBy(caller.userId())
                .build();

        sqsIngestionProducer.publishIngestRequested(event);
        publishAudit(AuditAction.DOCUMENT_VERSION_UPLOADED, "DOCUMENT", doc.getId().toString(),
                Map.of("version", nextVersion, "department", doc.getDepartment().name()));

        return toVersionDto(savedVersion);
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse getDownloadUrl(UUID documentId, Integer version, Integer page) {
        Document doc = findAuthorizedDocumentOrThrow(documentId);
        int targetVersion = version != null ? version : (doc.getCurrentVersion() != null ? doc.getCurrentVersion() : 1);

        Optional<DocumentVersion> dvOpt = documentVersionRepository.findByDocumentIdAndVersion(documentId, targetVersion);
        String bucket = dvOpt.map(DocumentVersion::getS3Bucket).orElse(s3StorageService.getDocumentsBucket());
        String s3Key = dvOpt.map(DocumentVersion::getS3Key)
                .orElseGet(() -> doc.getS3Key() != null
                        ? doc.getS3Key()
                        : s3StorageService.buildS3Key(doc.getDepartment().name(), doc.getId(), targetVersion, doc.getFileName()));

        publishAudit(AuditAction.DOCUMENT_VIEWED, "DOCUMENT", doc.getId().toString(),
                Map.of("version", targetVersion, "action", "DOWNLOAD_URL"));
        return s3StorageService.generatePresignedDownloadUrl(bucket, s3Key, page);
    }

    @Transactional(readOnly = true)
    public ChunkResponse getChunk(UUID documentId, UUID chunkId) {
        Document doc = findAuthorizedDocumentOrThrow(documentId);
        DocumentChunk chunk = documentChunkRepository.findByIdAndDocumentIdAndIsDeletedFalse(chunkId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("CHUNK_NOT_FOUND",
                        "Chunk not found with ID: " + chunkId));

        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        boolean deptAllowed = caller.allowedDepartments().stream()
                .anyMatch(d -> d.name().equalsIgnoreCase(chunk.getDepartmentCode()));
        if (!deptAllowed) {
            publishAudit(AuditAction.ACCESS_DENIED, "DOCUMENT", documentId.toString(),
                    Map.of("chunkId", chunkId.toString(), "documentDepartment", chunk.getDepartmentCode()));
            throw new ResourceNotFoundException("DOCUMENT_NOT_FOUND", "Document or chunk not found");
        }

        return ChunkResponse.builder()
                .id(chunk.getId())
                .documentId(chunk.getDocumentId())
                .documentTitle(chunk.getDocumentTitle() != null ? chunk.getDocumentTitle() : doc.getTitle())
                .version(chunk.getVersion())
                .chunkIndex(chunk.getChunkIndex())
                .pageNumber(chunk.getPageNumber())
                .sectionTitle(chunk.getSectionTitle())
                .content(chunk.getContent())
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getDocumentStatus(UUID documentId) {
        Document document = findAuthorizedDocumentOrThrow(documentId);
        IngestionJob job = ingestionJobRepository.findTopByDocumentIdOrderByCreatedAtDesc(documentId)
                .orElse(null);

        return DocumentStatusResponse.builder()
                .documentId(document.getId())
                .jobId(job != null ? job.getId() : null)
                .status(document.getStatus())
                .progressPercent(job != null ? job.getProgressPercent() : (document.getStatus() == DocumentStatus.INDEXED ? 100 : 0))
                .errorMessage(job != null ? job.getErrorMessage() : null)
                .updatedAt(job != null ? job.getUpdatedAt() : document.getUpdatedAt())
                .build();
    }

    public Document findAuthorizedDocumentOrThrow(UUID documentId) {
        Document doc = documentRepository.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("DOCUMENT_NOT_FOUND",
                        "Document not found with ID: " + documentId));

        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        if (!caller.allowedDepartments().contains(doc.getDepartment())) {
            publishAudit(AuditAction.ACCESS_DENIED, "DOCUMENT", documentId.toString(),
                    Map.of("documentDepartment", doc.getDepartment().name(), "callerDepartment", caller.department().name()));
            throw new ResourceNotFoundException("DOCUMENT_NOT_FOUND",
                    "Document not found with ID: " + documentId);
        }
        return doc;
    }

    /**
     * Enforces contracts/auth.md RBAC write rules:
     * - ADMIN: may upload/update/delete documents in any department (including GENERAL)
     * - MANAGER: may upload/update/delete documents ONLY in their own department (GENERAL is ADMIN-only)
     * - EMPLOYEE: may never upload/update/delete documents
     */
    public void enforceWritePermission(Department targetDepartment, String resourceId) {
        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        String role = caller.role();
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        if ("MANAGER".equalsIgnoreCase(role)
                && targetDepartment != null
                && targetDepartment != Department.GENERAL
                && targetDepartment == caller.department()) {
            return;
        }

        publishAudit(AuditAction.ACCESS_DENIED, "DOCUMENT", resourceId,
                Map.of(
                        "callerRole", role,
                        "callerDepartment", caller.department() != null ? caller.department().name() : "NONE",
                        "targetDepartment", targetDepartment != null ? targetDepartment.name() : "NONE"));
        throw new ForbiddenException("Caller role '" + role + "' in department '" + caller.department()
                + "' is not permitted to modify documents in department '" + targetDepartment + "'");
    }

    private void publishAudit(AuditAction action, String resourceType, String resourceId, Map<String, Object> metadata) {
        if (auditEventPublisher != null) {
            auditEventPublisher.publish(action, resourceType, resourceId, metadata);
        }
    }

    private byte[] readAndValidateFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        long maxBytes = maxUploadMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new PayloadTooLargeException("File size exceeds maximum limit of " + maxUploadMb + " MB");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Uploaded file cannot be empty");
            }
            if (bytes.length > maxBytes) {
                throw new PayloadTooLargeException("File size exceeds maximum limit of " + maxUploadMb + " MB");
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file stream", e);
        }
    }

    public String detectContentTypeByMagicBytes(MultipartFile file, byte[] bytes) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";

        if (bytes.length >= 5
                && bytes[0] == 0x25
                && bytes[1] == 0x50
                && bytes[2] == 0x44
                && bytes[3] == 0x46
                && bytes[4] == 0x2D) {
            return MIME_PDF;
        }

        if (bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4B
                && bytes[2] == 0x03
                && bytes[3] == 0x04
                && (filename.endsWith(".docx") || MIME_DOCX.equalsIgnoreCase(file.getContentType()))) {
            return MIME_DOCX;
        }

        throw new UnsupportedMediaTypeException(
                "Only valid PDF (%PDF-) and DOCX (OpenXML ZIP) files are supported");
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "updatedAt");
        }
        String[] parts = sortParam.split(",");
        String prop = parts[0].trim();
        if (!Set.of("createdAt", "updatedAt", "title", "status", "department").contains(prop)) {
            prop = "updatedAt";
        }
        Sort.Direction dir = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(dir, prop);
    }

    private DocumentResponse toDocumentResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .department(doc.getDepartment())
                .category(doc.getCategory() != null ? doc.getCategory() : DocumentCategory.OTHER)
                .author(doc.getAuthor())
                .tags(doc.getTagList())
                .currentVersion(doc.getCurrentVersion() != null ? doc.getCurrentVersion() : 1)
                .status(doc.getStatus())
                .originalFilename(doc.getFileName())
                .contentType(doc.getFileType())
                .pageCount(doc.getPageCount())
                .createdBy(doc.getCreatedBy())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .classification(doc.getClassification())
                .version(doc.getCurrentVersion() != null ? doc.getCurrentVersion() : doc.getVersion())
                .build();
    }

    private DocumentVersionDto toVersionDto(DocumentVersion v) {
        return DocumentVersionDto.builder()
                .id(v.getId())
                .documentId(v.getDocumentId())
                .version(v.getVersion())
                .status(v.getStatus())
                .originalFilename(v.getOriginalFilename())
                .contentType(v.getContentType())
                .sizeBytes(v.getSizeBytes())
                .pageCount(v.getPageCount())
                .chunkCount(v.getChunkCount())
                .changeNote(v.getChangeNote())
                .errorMessage(v.getErrorMessage())
                .uploadedBy(v.getUploadedBy())
                .uploadedAt(v.getUploadedAt())
                .indexedAt(v.getIndexedAt())
                .build();
    }
}
