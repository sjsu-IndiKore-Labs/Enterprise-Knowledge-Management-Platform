package com.helios.ekm.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentIngestRequestedEvent {

    @NotNull
    @Builder.Default
    private UUID eventId = UUID.randomUUID();

    @NotBlank
    @Builder.Default
    private String eventType = "DOCUMENT_INGEST_REQUESTED";

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @NotBlank
    private String traceId;

    @NotNull
    private UUID documentId;

    @NotNull
    private UUID versionId;

    @NotNull
    @Min(1)
    private Integer version;

    private UUID previousVersionId;

    @NotBlank
    private String s3Bucket;

    @NotBlank
    private String s3Key;

    @NotBlank
    private String contentType;

    @NotBlank
    private String originalFilename;

    private Long sizeBytes;

    @NotBlank
    private String title;

    @NotBlank
    private String department;

    @NotBlank
    private String category;

    private List<String> tags;

    private String author;

    @NotNull
    private UUID uploadedBy;
}

