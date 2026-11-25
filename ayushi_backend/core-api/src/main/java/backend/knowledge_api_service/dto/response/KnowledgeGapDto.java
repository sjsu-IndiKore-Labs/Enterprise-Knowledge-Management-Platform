package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.KnowledgeGapStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeGapDto {
    private UUID id;
    private String sampleQuery;
    private String normalizedQuery;
    private Department department;
    private Integer occurrences;
    private KnowledgeGapStatus status;
    private String notes;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant resolvedAt;
}

