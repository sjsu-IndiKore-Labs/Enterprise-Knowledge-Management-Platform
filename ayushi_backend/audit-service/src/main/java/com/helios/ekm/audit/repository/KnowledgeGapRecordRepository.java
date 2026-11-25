package com.helios.ekm.audit.repository;

import com.helios.ekm.audit.entity.KnowledgeGapRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeGapRecordRepository extends JpaRepository<KnowledgeGapRecord, UUID> {
    Optional<KnowledgeGapRecord> findByNormalizedQueryAndDepartmentCode(String normalizedQuery, String departmentCode);
}

