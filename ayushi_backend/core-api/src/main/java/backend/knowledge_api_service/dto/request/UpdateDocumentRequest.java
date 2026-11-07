package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.DocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Update document metadata payload")
public class UpdateDocumentRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private DocumentCategory category;

    @Size(max = 120, message = "Author must not exceed 120 characters")
    private String author;

    private List<String> tags;
}

