package backend.knowledge_api_service.dto.response;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    private UUID queryId;
    private UUID conversationId;
    private UUID messageId;
    private String answer;
    private Boolean answered;
    private Double confidence;
    private List<CitationDto> citations;
    private Integer latencyMs;
}

