package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.DocumentStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersionDto {
    private UUID id;
    private UUID documentId;
    private Integer version;
    private DocumentStatus status;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private Integer pageCount;
    private Integer chunkCount;
    private String changeNote;
    private String errorMessage;
    private UUID uploadedBy;
    private Instant uploadedAt;
    private Instant indexedAt;
}

