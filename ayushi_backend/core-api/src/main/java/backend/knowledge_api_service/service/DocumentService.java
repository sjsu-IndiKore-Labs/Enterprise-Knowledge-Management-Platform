package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.request.DocumentUploadRequest;
import backend.knowledge_api_service.dto.response.DocumentResponse;
import backend.knowledge_api_service.dto.response.DocumentStatusResponse;
import backend.knowledge_api_service.dto.response.DocumentUploadResponse;
import backend.knowledge_api_service.entity.Document;
import backend.knowledge_api_service.entity.IngestionJob;
import backend.knowledge_api_service.model.DocumentStatus;
import backend.knowledge_api_service.repository.DocumentRepository;
import backend.knowledge_api_service.repository.IngestionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx");

    @Transactional
    public DocumentUploadResponse processUpload(MultipartFile file, DocumentUploadRequest metadata) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        // 1. Persist document metadata with PENDING status
        Document document = Document.builder()
                .title(metadata.getTitle())
                .fileName(originalFilename)
                .fileType(contentType)
                .fileSize(file.getSize())
                .department(metadata.getDepartment())
                .classification(metadata.getClassification())
                .version(1)
                .status(DocumentStatus.PENDING)
                .build();

        Document savedDoc = documentRepository.save(document);

        // 2. Create ingestion job entry
        IngestionJob job = IngestionJob.builder()
                .document(savedDoc)
                .status(DocumentStatus.PENDING)
                .progressPercent(0)
                .build();

        IngestionJob savedJob = ingestionJobRepository.save(job);

        log.info("Document accepted for ingestion: docId={}, jobId={}, title={}", 
                savedDoc.getId(), savedJob.getId(), savedDoc.getTitle());

        return DocumentUploadResponse.builder()
                .documentId(savedDoc.getId())
                .jobId(savedJob.getId())
                .title(savedDoc.getTitle())
                .fileName(savedDoc.getFileName())
                .status(savedDoc.getStatus())
                .message("Document uploaded successfully and queued for background indexing.")
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentStatusResponse getDocumentStatus(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        IngestionJob job = ingestionJobRepository.findTopByDocumentIdOrderByCreatedAtDesc(documentId)
                .orElse(null);

        return DocumentStatusResponse.builder()
                .documentId(document.getId())
                .jobId(job != null ? job.getId() : null)
                .status(document.getStatus())
                .progressPercent(job != null ? job.getProgressPercent() : 0)
                .errorMessage(job != null ? job.getErrorMessage() : null)
                .updatedAt(job != null ? job.getUpdatedAt() : document.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments() {
        return documentRepository.findAll().stream()
                .map(doc -> DocumentResponse.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .fileName(doc.getFileName())
                        .fileType(doc.getFileType())
                        .fileSize(doc.getFileSize())
                        .department(doc.getDepartment())
                        .classification(doc.getClassification())
                        .version(doc.getVersion())
                        .status(doc.getStatus())
                        .createdAt(doc.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 50MB limit");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename is missing");
        }

        String lower = filename.toLowerCase();
        boolean validExt = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!validExt) {
            throw new IllegalArgumentException("Only PDF and DOCX files are supported");
        }
    }
}
