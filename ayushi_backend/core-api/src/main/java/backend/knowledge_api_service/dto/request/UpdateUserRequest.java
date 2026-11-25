package backend.knowledge_api_service.dto.request;

import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {
    private String fullName;
    private Role role;
    private Department department;
    private Boolean active;
}

