package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Status details of an ongoing or completed document ingestion job")
public class DocumentStatusResponse {

    @Schema(description = "Document identifier", example = "d3b07384-d113-494b-9c8e-aa02a632001e")
    private UUID documentId;

    @Schema(description = "Ingestion job identifier", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private UUID jobId;

    @Schema(description = "Current lifecycle stage", example = "INDEXED")
    private DocumentStatus status;

    @Schema(description = "Progress percentage from 0 to 100", example = "100")
    private Integer progressPercent;

    @Schema(description = "Error details if processing failed", example = "null")
    private String errorMessage;

    @Schema(description = "Timestamp of the last status update")
    private Instant updatedAt;
}
