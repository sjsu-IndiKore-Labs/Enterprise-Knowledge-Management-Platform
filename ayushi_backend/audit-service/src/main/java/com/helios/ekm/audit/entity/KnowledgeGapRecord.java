package com.helios.ekm.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_gaps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeGapRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "normalized_query", nullable = false, columnDefinition = "TEXT")
    private String normalizedQuery;

    @Column(name = "sample_query", nullable = false, columnDefinition = "TEXT")
    private String sampleQuery;

    @Column(name = "department_code", nullable = false, length = 32)
    private String departmentCode;

    @Column(name = "occurrences", nullable = false)
    @Builder.Default
    private Integer occurrences = 1;

    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "topic_label", length = 200)
    private String topicLabel;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}

