package backend.knowledge_api_service.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkResponse {
    private UUID id;
    private UUID documentId;
    private String documentTitle;
    private Integer version;
    private Integer chunkIndex;
    private Integer pageNumber;
    private String sectionTitle;
    private String content;
}

