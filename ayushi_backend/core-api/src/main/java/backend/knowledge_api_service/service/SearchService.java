package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.request.SearchRequest;
import backend.knowledge_api_service.dto.response.SearchResponse;
import backend.knowledge_api_service.dto.response.SearchResultItem;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.SearchMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final AiServiceClient aiServiceClient;

    public SearchResponse search(SearchRequest request) {
        RequestIdFilter.CallerInfo caller = RequestIdFilter.getCurrentCaller();
        List<Department> callerAllowed = caller.allowedDepartments();

        // Intersect requested departments (or legacy authorizedDepartments) with caller's allowedDepartments
        List<Department> requestedDepts = request.getDepartments() != null && !request.getDepartments().isEmpty()
                ? request.getDepartments()
                : request.getAuthorizedDepartments();

        List<Department> effectiveDepartments;
        if (requestedDepts != null && !requestedDepts.isEmpty()) {
            effectiveDepartments = requestedDepts.stream()
                    .filter(callerAllowed::contains)
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            effectiveDepartments = new ArrayList<>(callerAllowed);
        }

        SearchMode mode = request.getMode() != null ? request.getMode() : SearchMode.HYBRID;
        int topK = request.getTopK() != null ? request.getTopK() : 10;

        if (effectiveDepartments.isEmpty()) {
            return SearchResponse.builder()
                    .query(request.getQuery())
                    .mode(mode)
                    .results(Collections.emptyList())
                    .totalResults(0)
                    .latencyMs(0)
                    .build();
        }

        log.info("Executing hybrid search via ai-service: query='{}', mode={}, allowedDepartments={}, topK={}",
                request.getQuery(), mode, effectiveDepartments, topK);

        AiServiceClient.InternalSearchRequest internalRequest = AiServiceClient.InternalSearchRequest.builder()
                .query(request.getQuery())
                .allowedDepartments(effectiveDepartments)
                .mode(mode)
                .topK(topK)
                .categories(request.getCategories())
                .build();

        AiServiceClient.InternalSearchResponse aiResponse = aiServiceClient.search(internalRequest);
        List<SearchResultItem> results = aiResponse.getResults() != null
                ? aiResponse.getResults()
                : Collections.emptyList();

        // Normalize backwards-compatible fields (snippet <-> content, similarityScore <-> score)
        for (SearchResultItem item : results) {
            if (item.getContent() == null && item.getSnippet() != null) {
                item.setContent(item.getSnippet());
            }
            if (item.getSnippet() == null && item.getContent() != null) {
                item.setSnippet(item.getContent());
            }
            if (item.getScore() == null && item.getSimilarityScore() != null) {
                item.setScore(item.getSimilarityScore());
            }
            if (item.getSimilarityScore() == null && item.getScore() != null) {
                item.setSimilarityScore(item.getScore());
            }
            if (item.getVersion() == null) {
                item.setVersion(1);
            }
        }

        return SearchResponse.builder()
                .query(request.getQuery())
                .mode(aiResponse.getMode() != null ? aiResponse.getMode() : mode)
                .results(results)
                .totalResults(results.size())
                .latencyMs(aiResponse.getLatencyMs() != null ? aiResponse.getLatencyMs() : 0)
                .build();
    }
}
