package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
    List<ChatMessageEntity> findTop6ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}

