package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    Optional<DocumentChunk> findByIdAndDocumentIdAndIsDeletedFalse(UUID id, UUID documentId);

    @Modifying
    @Query("UPDATE DocumentChunk c SET c.isDeleted = true WHERE c.documentId = :documentId")
    int markChunksDeletedByDocumentId(@Param("documentId") UUID documentId);

    @Modifying
    @Query("UPDATE DocumentChunk c SET c.isCurrent = false WHERE c.documentId = :documentId AND c.version <> :currentVersion")
    int markOtherVersionsNotCurrent(@Param("documentId") UUID documentId, @Param("currentVersion") Integer currentVersion);
}

