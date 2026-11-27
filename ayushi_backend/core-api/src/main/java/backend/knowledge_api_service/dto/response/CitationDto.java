package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.Department;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CitationDto {
    private Integer citationNumber;
    private UUID documentId;
    private String documentTitle;
    private Department department;
    private Integer version;
    private Integer pageNumber;
    private String sectionTitle;
    private UUID chunkId;
    private String snippet;
    private Double score;
}

