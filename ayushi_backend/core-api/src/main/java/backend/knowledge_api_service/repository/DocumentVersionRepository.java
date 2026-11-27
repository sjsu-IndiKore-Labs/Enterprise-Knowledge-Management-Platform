package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(UUID documentId);
    Optional<DocumentVersion> findByDocumentIdAndVersion(UUID documentId, Integer version);
    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionDesc(UUID documentId);
}

