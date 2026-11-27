package com.helios.ekm.audit.repository;

import com.helios.ekm.audit.entity.QueryLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QueryLogRecordRepository extends JpaRepository<QueryLogRecord, UUID> {
}

