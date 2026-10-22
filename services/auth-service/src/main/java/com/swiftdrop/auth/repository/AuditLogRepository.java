package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    List<AuditLogEntry> findByTarget(String target);
}
