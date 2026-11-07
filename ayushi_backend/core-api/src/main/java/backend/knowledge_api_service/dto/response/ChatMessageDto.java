package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.MessageRole;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {
    private UUID id;
    private MessageRole role;
    private String content;
    private List<CitationDto> citations;
    private Boolean answered;
    private String feedback;
    private Instant createdAt;
}

