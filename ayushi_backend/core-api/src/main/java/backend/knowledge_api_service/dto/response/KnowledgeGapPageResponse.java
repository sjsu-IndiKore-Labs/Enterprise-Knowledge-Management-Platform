package backend.knowledge_api_service.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeGapPageResponse {
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private List<KnowledgeGapDto> items;
}

