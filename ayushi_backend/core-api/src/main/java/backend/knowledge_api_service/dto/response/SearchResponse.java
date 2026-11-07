package backend.knowledge_api_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Hybrid search result response containing ranked passages with citations")
public class SearchResponse {

    @Schema(description = "The original search query executed")
    private String query;

    @Schema(description = "Count of retrieved matching passages")
    private Integer totalResults;

    @Schema(description = "Ranked list of citation passages")
    private List<SearchResultItem> results;
}
