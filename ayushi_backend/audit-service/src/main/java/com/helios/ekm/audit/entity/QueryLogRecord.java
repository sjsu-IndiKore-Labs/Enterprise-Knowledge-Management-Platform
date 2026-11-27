package com.helios.ekm.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryLogRecord {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "department_code", length = 32)
    private String departmentCode;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "normalized_query", nullable = false, columnDefinition = "TEXT")
    private String normalizedQuery;

    @Column(name = "allowed_departments", nullable = false)
    @Builder.Default
    private String allowedDepartments = "";

    @Column(name = "answered", nullable = false)
    @Builder.Default
    private Boolean answered = false;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "cited_document_ids", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String citedDocumentIds = "";

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

