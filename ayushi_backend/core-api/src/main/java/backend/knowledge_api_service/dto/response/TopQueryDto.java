package backend.knowledge_api_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopQueryDto {
    private String query;
    private long count;
    private double answeredRate;
}

