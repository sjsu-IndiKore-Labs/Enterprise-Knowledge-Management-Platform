package backend.knowledge_api_service.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationDetailResponse {
    private UUID id;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ChatMessageDto> messages;
}

