package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.DocumentUploadRequest;
import backend.knowledge_api_service.dto.request.UpdateDocumentRequest;
import backend.knowledge_api_service.dto.response.*;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentCategory;
import backend.knowledge_api_service.model.DocumentStatus;
import backend.knowledge_api_service.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "APIs for uploading, versioning, listing, and managing enterprise documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a new document (Contract endpoint)",
        description = "Validates file magic bytes (PDF/DOCX) and size <= 25MB, stores in S3, creates document + version 1 (QUEUED), and publishes DOCUMENT_INGEST_REQUESTED."
    )
    public ResponseEntity<DocumentResponse> uploadDocumentContract(
            @Parameter(description = "PDF or DOCX file (max 25MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Valid @ModelAttribute DocumentUploadRequest metadata) {

        DocumentResponse response = documentService.createAndIngestDocument(file, metadata);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload enterprise document (Handoff alias)",
        description = "Accepts a PDF or DOCX file along with department and metadata, returning 202 Accepted with job ID."
    )
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @Parameter(description = "PDF or Word document file", required = true)
            @RequestParam("file") MultipartFile file,
            @Valid @ModelAttribute DocumentUploadRequest metadata) {

        DocumentUploadResponse response = documentService.processUpload(file, metadata);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @Operation(
        summary = "List documents the caller is allowed to see",
        description = "Supports pagination and filtering by department, category, status, and keyword query."
    )
    public ResponseEntity<DocumentPageResponse> listDocuments(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "updatedAt,desc") String sort,
            @RequestParam(name = "department", required = false) Department department,
            @RequestParam(name = "category", required = false) DocumentCategory category,
            @RequestParam(name = "status", required = false) DocumentStatus status,
            @RequestParam(name = "q", required = false) String q) {

        DocumentPageResponse response = documentService.listDocumentsPage(
                page, size, sort, department, category, status, q);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Document metadata incl. processing status and all versions")
    public ResponseEntity<DocumentDetailResponse> getDocument(
            @PathVariable("documentId") UUID documentId) {
        return ResponseEntity.ok(documentService.getDocumentDetail(documentId));
    }

    @PatchMapping("/{documentId}")
    @Operation(summary = "Update document metadata (title, category, author, tags)")
    public ResponseEntity<DocumentResponse> updateDocumentMetadata(
            @PathVariable("documentId") UUID documentId,
            @Valid @RequestBody UpdateDocumentRequest request) {
        return ResponseEntity.ok(documentService.updateDocumentMetadata(documentId, request));
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "Soft-delete a document and mark its chunks deleted")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("documentId") UUID documentId) {
        documentService.softDeleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a new version of an existing document")
    public ResponseEntity<DocumentVersionDto> uploadDocumentVersion(
            @PathVariable("documentId") UUID documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "changeNote", required = false) String changeNote) {

        DocumentVersionDto versionDto = documentService.uploadNewVersion(documentId, file, changeNote);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(versionDto);
    }

    @GetMapping("/{documentId}/download-url")
    @Operation(summary = "Short-lived (5 min) presigned S3 URL to view/download the file")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable("documentId") UUID documentId,
            @RequestParam(name = "version", required = false) Integer version,
            @RequestParam(name = "page", required = false) Integer page) {

        return ResponseEntity.ok(documentService.getDownloadUrl(documentId, version, page));
    }

    @GetMapping("/{documentId}/chunks/{chunkId}")
    @Operation(summary = "Full text of one chunk (used by the citation preview panel)")
    public ResponseEntity<ChunkResponse> getChunk(
            @PathVariable("documentId") UUID documentId,
            @PathVariable("chunkId") UUID chunkId) {

        return ResponseEntity.ok(documentService.getChunk(documentId, chunkId));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get document ingestion status")
    public ResponseEntity<DocumentStatusResponse> getDocumentStatus(
            @PathVariable("id") UUID id) {
        return ResponseEntity.ok(documentService.getDocumentStatus(id));
    }
}
