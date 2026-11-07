package backend.knowledge_api_service.dto.response;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadUrlResponse {
    private String url;
    private Instant expiresAt;
}

