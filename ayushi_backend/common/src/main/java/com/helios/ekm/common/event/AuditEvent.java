package com.helios.ekm.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditEvent {

    @NotNull
    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    @NotBlank
    @Builder.Default
    private String eventType = "AUDIT_EVENT";

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @NotBlank
    private String traceId;

    @NotBlank
    private String action;

    private UUID actorUserId;

    private String actorEmail;

    private String actorRole;

    private String actorDepartment;

    private String resourceType;

    private String resourceId;

    private String ipAddress;

    private String userAgent;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    public static String normalizeQuery(String rawQuery) {
        if (rawQuery == null) {
            return "";
        }
        return rawQuery.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}

