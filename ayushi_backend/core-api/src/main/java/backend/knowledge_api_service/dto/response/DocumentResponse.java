package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.ClassificationLevel;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentCategory;
import backend.knowledge_api_service.model.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Enterprise document metadata")
public class DocumentResponse {

    private UUID id;
    private String title;
    private Department department;
    private DocumentCategory category;
    private String author;
    private List<String> tags;
    private Integer currentVersion;
    private DocumentStatus status;
    private String originalFilename;
    private String contentType;
    private Integer pageCount;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    // Backwards-compatible aliases from Milestone 1/2 handoff
    private String fileName;
    private String fileType;
    private Long fileSize;
    private ClassificationLevel classification;
    private Integer version;
}
