package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.ClassificationLevel;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Metadata form for uploading an enterprise document")
public class DocumentUploadRequest {

    @NotBlank(message = "Document title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    @Schema(description = "Display title of the document", example = "2026 Employee Health & Benefits Guide")
    private String title;

    @NotNull(message = "Department is required")
    @Schema(description = "Owning enterprise department", example = "HR")
    private Department department;

    @Builder.Default
    @Schema(description = "Document category", example = "POLICY")
    private DocumentCategory category = DocumentCategory.OTHER;

    @Size(max = 120, message = "Author must not exceed 120 characters")
    @Schema(description = "Document author", example = "People Operations")
    private String author;

    @Schema(description = "Comma-separated tags, e.g. 'leave,pto,benefits'", example = "leave,pto,benefits")
    private String tags;

    @Builder.Default
    @Schema(description = "Security classification level", example = "INTERNAL")
    private ClassificationLevel classification = ClassificationLevel.INTERNAL;
}
