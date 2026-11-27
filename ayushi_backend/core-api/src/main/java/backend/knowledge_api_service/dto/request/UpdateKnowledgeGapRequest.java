package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.KnowledgeGapStatus;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateKnowledgeGapRequest {
    private KnowledgeGapStatus status;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}

