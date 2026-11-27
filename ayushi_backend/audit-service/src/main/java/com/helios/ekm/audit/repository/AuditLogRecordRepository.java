package com.helios.ekm.audit.repository;

import com.helios.ekm.audit.entity.AuditLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRecordRepository extends JpaRepository<AuditLogRecord, Long> {
    boolean existsByEventId(UUID eventId);
}

