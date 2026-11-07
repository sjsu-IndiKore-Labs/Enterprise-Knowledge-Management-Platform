package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.Document;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {
    List<Document> findByDepartmentOrderByCreatedAtDesc(Department department);
    List<Document> findByStatusOrderByCreatedAtDesc(DocumentStatus status);
    Optional<Document> findByIdAndDeletedAtIsNull(UUID id);
}
