package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
    @Schema(description = "Natural language search question or keyword phrase", example = "What is the policy for parental leave?")
    private String query;

    @Schema(description = "Departments the requesting user is authorized to search across", example = "[\"HR\", \"GENERAL\"]")
    private List<Department> authorizedDepartments;

    @Builder.Default
    @Schema(description = "Maximum number of ranked passages to return", example = "5")
    private Integer topK = 5;

    @Builder.Default
    @Schema(description = "Minimum cosine similarity score threshold (0.0 to 1.0)", example = "0.72")
    private Double minScore = 0.72;
}
