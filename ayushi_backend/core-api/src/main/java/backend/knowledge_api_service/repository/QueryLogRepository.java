package backend.knowledge_api_service.repository;

import backend.knowledge_api_service.entity.QueryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface QueryLogRepository extends JpaRepository<QueryLogEntity, UUID> {
    List<QueryLogEntity> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant from, Instant to);
}

