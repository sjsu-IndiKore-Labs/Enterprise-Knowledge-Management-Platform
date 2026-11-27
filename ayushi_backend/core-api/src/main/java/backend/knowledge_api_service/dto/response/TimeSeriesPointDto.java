package backend.knowledge_api_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSeriesPointDto {
    private String date;
    private long queries;
    private long unanswered;
}

