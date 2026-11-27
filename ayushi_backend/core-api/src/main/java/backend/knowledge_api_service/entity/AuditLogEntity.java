package backend.knowledge_api_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "action", nullable = false, length = 48)
    private String action;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "actor_role", length = 16)
    private String actorRole;

    @Column(name = "actor_department", length = 32)
    private String actorDepartment;

    @Column(name = "resource_type", length = 32)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "metadata", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String metadata = "{}";

    @PrePersist
    public void onPrePersist() {
        if (this.occurredAt == null) {
            this.occurredAt = Instant.now();
        }
        if (this.metadata == null) {
            this.metadata = "{}";
        }
    }
}

