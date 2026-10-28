package backend.knowledge_api_service.controller;

import backend.knowledge_api_service.dto.request.SearchRequest;
import backend.knowledge_api_service.dto.response.SearchResponse;
import backend.knowledge_api_service.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Hybrid Search & Citations", description = "Semantic vector and lexical keyword search across enterprise documents")
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    @Operation(
        summary = "Execute hybrid vector and keyword search",
        description = "Performs high-precision retrieval across indexed documents using pgvector cosine similarity " +
                      "and lexical full-text search, enforcing department-level isolation to prevent cross-department data leakage."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Matching ranked passages and citations returned",
                     content = @Content(schema = @Schema(implementation = SearchResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid search payload")
    })
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        SearchResponse response = searchService.search(request);
        return ResponseEntity.ok(response);
    }
}
