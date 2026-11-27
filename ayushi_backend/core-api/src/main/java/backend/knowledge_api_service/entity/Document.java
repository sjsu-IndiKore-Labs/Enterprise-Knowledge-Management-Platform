package backend.knowledge_api_service.entity;

import backend.knowledge_api_service.model.ClassificationLevel;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentCategory;
import backend.knowledge_api_service.model.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "file_name", nullable = false)
    @Builder.Default
    private String fileName = "document.pdf";

    @Column(name = "file_type", nullable = false, length = 64)
    @Builder.Default
    private String fileType = "application/pdf";

    @Column(name = "file_size", nullable = false)
    @Builder.Default
    private Long fileSize = 0L;

    @Column(name = "s3_key", length = 512)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    @Builder.Default
    private Department department = Department.GENERAL;

    @Column(name = "department_code", length = 32)
    private String departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    @Builder.Default
    private DocumentCategory category = DocumentCategory.OTHER;

    @Column(name = "author", length = 120)
    private String author;

    @Column(name = "tags", length = 1000, nullable = false)
    @Builder.Default
    private String tags = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    @Builder.Default
    private ClassificationLevel classification = ClassificationLevel.INTERNAL;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    @Column(name = "page_count")
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.QUEUED;

    @Column(name = "created_by")
    @Builder.Default
    private UUID createdBy = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    public void onPrePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.departmentCode == null && this.department != null) {
            this.departmentCode = this.department.name();
        }
        if (this.currentVersion == null) {
            this.currentVersion = this.version != null ? this.version : 1;
        }
        if (this.tags == null) {
            this.tags = "";
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = Instant.now();
        if (this.department != null) {
            this.departmentCode = this.department.name();
        }
    }

    public List<String> getTagList() {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void setTagList(List<String> tagList) {
        if (tagList == null || tagList.isEmpty()) {
            this.tags = "";
        } else {
            this.tags = tagList.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(","));
        }
    }
}
