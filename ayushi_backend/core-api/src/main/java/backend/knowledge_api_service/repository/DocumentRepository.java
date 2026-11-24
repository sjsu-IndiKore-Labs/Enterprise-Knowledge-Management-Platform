package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.Document;
import backend.knowledge_api_service.model.Department;
import backend.knowledge_api_service.model.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByDepartmentIn(List<Department> departments);
    List<Document> findByStatus(DocumentStatus status);
}
