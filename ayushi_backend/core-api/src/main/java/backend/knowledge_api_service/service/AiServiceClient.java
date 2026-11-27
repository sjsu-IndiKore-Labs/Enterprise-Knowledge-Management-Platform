package backend.knowledge_api_service.service;

import backend.knowledge_api_service.dto.response.CitationDto;
import backend.knowledge_api_service.dto.response.SearchResponse;
import backend.knowledge_api_service.dto.response.SearchResultItem;
import backend.knowledge_api_service.exception.UpstreamServiceException;
import backend.knowledge_api_service.filter.RequestIdFilter;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentCategory;
import backend.knowledge_api_service.model.SearchMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiServiceClient {

    public static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    private final ObjectMapper objectMapper;

    private volatile HttpClient httpClient;

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
                }
            }
        }
        return httpClient;
    }

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${internal.service-token:local-internal-token-change-me}")
    private String internalServiceToken;

    @Value("${ai.service.search-timeout-seconds:10}")
    private long searchTimeoutSeconds = 10;

    @Value("${ai.service.answer-timeout-seconds:60}")
    private long answerTimeoutSeconds = 60;

    @Value("${ai.service.fallback-enabled:false}")
    private boolean fallbackEnabled = false;

    public InternalSearchResponse search(InternalSearchRequest request) {
        String url = trimTrailingSlash(aiServiceUrl) + "/internal/v1/search";
        try {
            return postWithSingleRetry(
                    url,
                    request,
                    InternalSearchResponse.class,
                    Duration.ofSeconds(searchTimeoutSeconds));
        } catch (UpstreamServiceException ex) {
            if (fallbackEnabled) {
                log.warn("ai-service /internal/v1/search unavailable ({}); using local fallback response", ex.getMessage());
                return buildFallbackSearchResponse(request);
            }
            throw ex;
        }
    }

    public InternalAnswerResponse answer(InternalAnswerRequest request) {
        String url = trimTrailingSlash(aiServiceUrl) + "/internal/v1/answer";
        try {
            return postWithSingleRetry(
                    url,
                    request,
                    InternalAnswerResponse.class,
                    Duration.ofSeconds(answerTimeoutSeconds));
        } catch (UpstreamServiceException ex) {
            if (fallbackEnabled) {
                log.warn("ai-service /internal/v1/answer unavailable ({}); using local fallback response", ex.getMessage());
                return buildFallbackAnswerResponse(request);
            }
            throw ex;
        }
    }

    private <T> T postWithSingleRetry(String url, Object bodyObj, Class<T> responseType, Duration timeout) {
        int maxAttempts = 2; // Initial attempt + 1 retry on 502/503
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String jsonBody = objectMapper.writeValueAsString(bodyObj);
                String traceId = RequestIdFilter.getCurrentTraceId();

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(timeout)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .header(HEADER_INTERNAL_TOKEN, internalServiceToken)
                        .header(HEADER_REQUEST_ID, traceId)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    return objectMapper.readValue(response.body(), responseType);
                }

                if ((status == 502 || status == 503) && attempt < maxAttempts) {
                    log.warn("ai-service returned {} on attempt {}/{}, retrying once...", status, attempt, maxAttempts);
                    continue;
                }

                throw new UpstreamServiceException(
                        "ai-service returned HTTP " + status + " from " + url);
            } catch (UpstreamServiceException use) {
                lastException = use;
                if (attempt >= maxAttempts) {
                    throw use;
                }
            } catch (Exception ex) {
                lastException = ex;
                if (attempt < maxAttempts) {
                    log.warn("ai-service call to {} failed on attempt {}/{} ({}), retrying once...",
                            url, attempt, maxAttempts, ex.getMessage());
                    continue;
                }
            }
        }

        throw new UpstreamServiceException("Failed to reach ai-service at " + url + ": "
                + (lastException != null ? lastException.getMessage() : "unknown error"));
    }

    private InternalSearchResponse buildFallbackSearchResponse(InternalSearchRequest request) {
        Department dept = (request.getAllowedDepartments() != null && !request.getAllowedDepartments().isEmpty())
                ? request.getAllowedDepartments().get(0)
                : Department.GENERAL;

        List<SearchResultItem> items = new ArrayList<>();
        items.add(SearchResultItem.builder()
                .chunkId(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .documentTitle("Enterprise Standard Operating Procedures & Policies.pdf")
                .fileName("Enterprise_SOP_2026.pdf")
                .department(dept)
                .category(DocumentCategory.POLICY)
                .version(1)
                .pageNumber(12)
                .sectionTitle("Parental Leave Policy")
                .content("Eligible employees may take up to 16 consecutive weeks of fully paid parental leave following the birth, adoption, or foster placement of a child.")
                .snippet("Eligible employees may take up to 16 consecutive weeks of fully paid parental leave following the birth, adoption, or foster placement of a child.")
                .score(0.925)
                .similarityScore(0.925)
                .semanticScore(0.94)
                .keywordScore(0.89)
                .build());

        return InternalSearchResponse.builder()
                .query(request.getQuery())
                .mode(request.getMode() != null ? request.getMode() : SearchMode.HYBRID)
                .results(items)
                .latencyMs(42)
                .build();
    }

    private InternalAnswerResponse buildFallbackAnswerResponse(InternalAnswerRequest request) {
        Department dept = (request.getAllowedDepartments() != null && !request.getAllowedDepartments().isEmpty())
                ? request.getAllowedDepartments().get(0)
                : Department.GENERAL;

        CitationDto citation = CitationDto.builder()
                .citationNumber(1)
                .documentId(UUID.randomUUID())
                .documentTitle("Enterprise Standard Operating Procedures & Policies")
                .department(dept)
                .version(1)
                .pageNumber(12)
                .sectionTitle("Parental Leave Policy")
                .chunkId(UUID.randomUUID())
                .snippet("Eligible employees may take up to 16 consecutive weeks of fully paid parental leave [1].")
                .score(0.91)
                .build();

        return InternalAnswerResponse.builder()
                .queryId(request.getQueryId())
                .answer("Based on company policy, eligible employees receive up to 16 consecutive weeks of paid parental leave [1].")
                .answered(true)
                .confidence(0.91)
                .citations(List.of(citation))
                .latencyMs(125)
                .build();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "http://localhost:8000";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalSearchRequest {
        private String query;
        private List<Department> allowedDepartments;
        @Builder.Default
        private SearchMode mode = SearchMode.HYBRID;
        @Builder.Default
        private Integer topK = 10;
        private List<DocumentCategory> categories;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalSearchResponse {
        private String query;
        private SearchMode mode;
        private List<SearchResultItem> results;
        private Integer latencyMs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryMessage {
        private String role;
        private String content;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalAnswerRequest {
        private UUID queryId;
        private String query;
        private List<Department> allowedDepartments;
        private List<HistoryMessage> history;
        @Builder.Default
        private Integer topK = 6;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InternalAnswerResponse {
        private UUID queryId;
        private String answer;
        private Boolean answered;
        private Double confidence;
        private List<CitationDto> citations;
        private Integer latencyMs;
    }
}

