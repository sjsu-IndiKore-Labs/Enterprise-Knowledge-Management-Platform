package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.IngestionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
    Optional<IngestionJob> findTopByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
