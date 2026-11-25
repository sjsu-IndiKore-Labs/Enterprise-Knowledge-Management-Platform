package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.Role;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserDto {
    private UUID id;
    private String email;
    private String fullName;
    private Role role;
    private Department department;
    private Boolean active;
    private Instant createdAt;
    private List<Department> allowedDepartments;
    private List<String> permissions;
}

