package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.SearchMode;
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

    private String query;
    @Builder.Default
    private SearchMode mode = SearchMode.HYBRID;
    private List<SearchResultItem> results;
    private Integer latencyMs;
    private Integer totalResults;
}
