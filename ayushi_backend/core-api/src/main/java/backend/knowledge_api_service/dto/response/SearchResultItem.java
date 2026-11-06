package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Individual retrieved document passage with citation metadata")
public class SearchResultItem {

    @Schema(description = "Unique chunk identifier")
    private UUID chunkId;

    @Schema(description = "Parent document identifier")
    private UUID documentId;

    @Schema(description = "Document title for citation citation", example = "2026 Employee Health & Benefits Guide")
    private String documentTitle;

    @Schema(description = "Original filename", example = "Employee_Guide_2026.pdf")
    private String fileName;

    @Schema(description = "Document page number containing this passage", example = "14")
    private Integer pageNumber;

    @Schema(description = "Extracted textual passage snippet")
    private String snippet;

    @Schema(description = "Combined hybrid similarity score (0.0 to 1.0)", example = "0.895")
    private Double similarityScore;

    @Schema(description = "Department owning this passage", example = "HR")
    private Department department;
}
