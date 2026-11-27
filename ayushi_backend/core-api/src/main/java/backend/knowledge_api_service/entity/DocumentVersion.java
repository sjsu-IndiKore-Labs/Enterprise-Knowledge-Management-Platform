package backend.knowledge_api_service.entity;

import backend.knowledge_api_service.model.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;

    @Column(name = "s3_key", nullable = false, length = 1024)
    private String s3Key;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.QUEUED;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "uploaded_by", nullable = false)
    @Builder.Default
    private UUID uploadedBy = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @PrePersist
    public void onPrePersist() {
        if (this.uploadedAt == null) {
            this.uploadedAt = Instant.now();
        }
    }
}

