package com.helios.ekm.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentIngestStatusEvent {

    @NotNull
    private UUID eventId;

    @NotBlank
    @Builder.Default
    private String eventType = "DOCUMENT_INGEST_STATUS";

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @NotBlank
    private String traceId;

    @NotNull
    private UUID documentId;

    private UUID versionId;

    private UUID jobId;

    @NotBlank
    private String status;

    private Integer progressPercent;

    private Integer pageCount;

    private Integer chunkCount;

    private Integer embeddingTokens;

    private Integer processingMs;

    private String errorCode;

    private String errorMessage;
}

