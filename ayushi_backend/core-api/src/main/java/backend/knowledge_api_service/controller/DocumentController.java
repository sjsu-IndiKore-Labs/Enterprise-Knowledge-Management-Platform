package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.DocumentUploadRequest;
import backend.knowledge_api_service.dto.response.DocumentResponse;
import backend.knowledge_api_service.dto.response.DocumentStatusResponse;
import backend.knowledge_api_service.dto.response.DocumentUploadResponse;
import backend.knowledge_api_service.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Document Management", description = "APIs for uploading, listing, and tracking enterprise documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload enterprise document",
        description = "Accepts a PDF or DOCX file (up to 50MB) along with classification and department metadata. " +
                      "Persists the document and immediately returns 202 Accepted with a background ingestion job ID."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Document accepted and queued for indexing",
                     content = @Content(schema = @Schema(implementation = DocumentUploadResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid file or missing metadata"),
        @ApiResponse(responseCode = "413", description = "File exceeds 50MB size limit")
    })
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @Parameter(description = "PDF or Word document file (max 50MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Valid @ModelAttribute DocumentUploadRequest metadata) {

        DocumentUploadResponse response = documentService.processUpload(file, metadata);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}/status")
    @Operation(
        summary = "Get document ingestion status",
        description = "Returns the real-time background processing stage and percentage for progress bars in the UI."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Current ingestion status returned",
                     content = @Content(schema = @Schema(implementation = DocumentStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    public ResponseEntity<DocumentStatusResponse> getDocumentStatus(
            @Parameter(description = "Document UUID", required = true)
            @PathVariable("id") UUID id) {

        DocumentStatusResponse response = documentService.getDocumentStatus(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
        summary = "List enterprise documents",
        description = "Retrieves all indexed enterprise documents with their version and department metadata."
    )
    public ResponseEntity<List<DocumentResponse>> listDocuments() {
        return ResponseEntity.ok(documentService.listDocuments());
    }
}
