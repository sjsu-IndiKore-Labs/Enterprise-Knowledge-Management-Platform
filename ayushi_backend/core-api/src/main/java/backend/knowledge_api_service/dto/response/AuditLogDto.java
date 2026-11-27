package backend.knowledge_api_service.dto.response;

import backend.knowledge_api_service.model.AuditAction;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.Role;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private Long id;
    private Instant occurredAt;
    private AuditAction action;
    private UUID actorUserId;
    private String actorEmail;
    private Role actorRole;
    private Department actorDepartment;
    private String resourceType;
    private String resourceId;
    private Map<String, Object> metadata;
    private String ipAddress;
}

