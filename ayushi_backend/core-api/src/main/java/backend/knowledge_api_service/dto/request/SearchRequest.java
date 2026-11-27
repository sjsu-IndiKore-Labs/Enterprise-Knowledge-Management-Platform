package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentCategory;
import backend.knowledge_api_service.model.SearchMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Hybrid vector and keyword search request payload")
public class SearchRequest {

    @NotBlank(message = "Search query cannot be empty")
    @Size(min = 1, max = 1000, message = "Search query must be between 1 and 1000 characters")
    @Schema(description = "Natural language search question or keyword phrase", example = "What is the policy for parental leave?")
    private String query;

    @Builder.Default
    @Schema(description = "Search mode (HYBRID, SEMANTIC, KEYWORD)", example = "HYBRID")
    private SearchMode mode = SearchMode.HYBRID;

    @Builder.Default
    @Min(1)
    @Max(50)
    @Schema(description = "Maximum number of ranked passages to return", example = "10")
    private Integer topK = 10;

    @Schema(description = "Optional narrowing; intersected server-side with caller's allowedDepartments", example = "[\"HR\", \"GENERAL\"]")
    private List<Department> departments;

    @Schema(description = "Legacy alias for departments")
    private List<Department> authorizedDepartments;

    @Schema(description = "Optional category filter")
    private List<DocumentCategory> categories;

    @Builder.Default
    @Schema(description = "Minimum similarity score threshold (0.0 to 1.0)", example = "0.35")
    private Double minScore = 0.35;
}
