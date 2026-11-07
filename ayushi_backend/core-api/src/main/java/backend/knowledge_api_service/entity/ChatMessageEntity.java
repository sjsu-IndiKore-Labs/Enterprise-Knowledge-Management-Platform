package backend.knowledge_api_service.entity;

import backend.knowledge_api_service.model.MessageRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "citations", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String citations = "[]";

    @Column(name = "query_id")
    private UUID queryId;

    @Column(name = "answered")
    private Boolean answered;

    @Column(name = "feedback", length = 8)
    private String feedback;

    @Column(name = "feedback_comment", length = 1000)
    private String feedbackComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}

