package backend.knowledge_api_service.dto.response;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsSummaryResponse {
    private long totalQueries;
    private double answeredRate;
    private int avgLatencyMs;
    private int p95LatencyMs;
    private long activeUsers;
    private long totalDocuments;
    private long indexedDocuments;
    private long failedDocuments;
    private long openKnowledgeGaps;
    private Double positiveFeedbackRate;
    private Map<String, Long> queriesByDepartment;
}

