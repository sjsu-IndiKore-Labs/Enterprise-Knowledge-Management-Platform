package backend.knowledge_api_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackRequest {

    @NotBlank(message = "Rating is required")
    @Pattern(regexp = "UP|DOWN", message = "Rating must be UP or DOWN")
    private String rating;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;
}

