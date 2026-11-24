package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response returned immediately when a document is accepted for ingestion")
public class DocumentUploadResponse {

    @Schema(description = "Unique identifier of the created document", example = "d3b07384-d113-494b-9c8e-aa02a632001e")
    private UUID documentId;

    @Schema(description = "Unique asynchronous ingestion job ID for tracking progress", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID jobId;

    @Schema(description = "Title of the uploaded document", example = "2026 Employee Health & Benefits Guide")
    private String title;

    @Schema(description = "Original filename", example = "Employee_Guide_2026.pdf")
    private String fileName;

    @Schema(description = "Initial processing status", example = "PENDING")
    private DocumentStatus status;

    @Schema(description = "Human-readable status summary", example = "Document uploaded successfully and queued for background indexing.")
    private String message;
}
