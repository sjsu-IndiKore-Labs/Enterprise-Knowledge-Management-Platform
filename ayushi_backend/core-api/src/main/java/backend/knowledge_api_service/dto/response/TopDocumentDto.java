package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.Department;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopDocumentDto {
    private UUID documentId;
    private String title;
    private Department department;
    private long citationCount;
}

