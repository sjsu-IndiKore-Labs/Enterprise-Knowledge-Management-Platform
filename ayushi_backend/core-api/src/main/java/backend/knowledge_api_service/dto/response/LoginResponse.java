package backend.knowledge_api_service.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    @Builder.Default
    private Integer expiresIn = 3600;
    private CurrentUserDto user;
}

