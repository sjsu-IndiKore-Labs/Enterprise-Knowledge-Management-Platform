package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.ClassificationLevel;
import backend.knowledge_api_service.model.Department;
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
@Schema(description = "Enterprise document metadata summary")
public class DocumentResponse {

    @Schema(description = "Document identifier")
    private UUID id;

    @Schema(description = "Document display title")
    private String title;

    @Schema(description = "Original filename")
    private String fileName;

    @Schema(description = "MIME file type")
    private String fileType;

    @Schema(description = "File size in bytes")
    private Long fileSize;

    @Schema(description = "Owning department")
    private Department department;

    @Schema(description = "Security classification")
    private ClassificationLevel classification;

    @Schema(description = "Document version")
    private Integer version;

    @Schema(description = "Current processing status")
    private DocumentStatus status;

    @Schema(description = "Timestamp when document was uploaded")
    private Instant createdAt;
}
