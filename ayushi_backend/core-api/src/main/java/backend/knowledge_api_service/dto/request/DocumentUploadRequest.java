package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.ClassificationLevel;
import backend.knowledge_api_service.model.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Metadata required for uploading an enterprise document")
public class DocumentUploadRequest {

    @NotBlank(message = "Document title is required")
    @Schema(description = "Display title of the document", example = "2026 Employee Health & Benefits Guide")
    private String title;

    @NotNull(message = "Department is required")
    @Schema(description = "Owning enterprise department", example = "HR")
    private Department department;

    @NotNull(message = "Classification level is required")
    @Schema(description = "Security classification level", example = "INTERNAL")
    private ClassificationLevel classification;
}
