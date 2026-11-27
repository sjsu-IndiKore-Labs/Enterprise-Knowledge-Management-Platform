package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.Department;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to ask the AI assistant a grounded question")
public class ChatRequest {

    @NotBlank(message = "Message cannot be empty")
    @Size(min = 1, max = 2000, message = "Message must be between 1 and 2000 characters")
    @Schema(description = "Natural-language question for the assistant", example = "How many days of parental leave do employees get?")
    private String message;

    @Schema(description = "Existing conversation ID, or null to start a new conversation")
    private UUID conversationId;

    @Schema(description = "Optional department filter; intersected server-side with allowedDepartments")
    private List<Department> departments;
}

