package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Individual retrieved document passage with citation metadata")
public class SearchResultItem {

    private UUID chunkId;
    private UUID documentId;
    private String documentTitle;
    private Department department;
    private DocumentCategory category;
    private Integer version;
    private Integer pageNumber;
    private String sectionTitle;
    private String content;
    private Double score;
    private Double semanticScore;
    private Double keywordScore;

    // Backwards-compatible aliases for Milestone 1/2 handoff
    private String fileName;
    private String snippet;
    private Double similarityScore;
}
