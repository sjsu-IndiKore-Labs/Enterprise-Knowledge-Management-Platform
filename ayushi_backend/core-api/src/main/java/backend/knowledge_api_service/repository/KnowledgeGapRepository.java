package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.KnowledgeGapEntity;
import backend.knowledge_api_service.model.KnowledgeGapStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeGapRepository extends JpaRepository<KnowledgeGapEntity, UUID>, JpaSpecificationExecutor<KnowledgeGapEntity> {
    Optional<KnowledgeGapEntity> findByNormalizedQueryAndDepartmentCode(String normalizedQuery, String departmentCode);
    long countByStatus(KnowledgeGapStatus status);
}

