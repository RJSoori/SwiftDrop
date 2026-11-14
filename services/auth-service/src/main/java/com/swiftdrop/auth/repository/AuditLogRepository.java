package com.swiftdrop.auth.repository;

import com.swiftdrop.auth.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/** Specifications power the admin audit-log page (filter by action and actor, with paging). */
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID>, JpaSpecificationExecutor<AuditLogEntry> {

    List<AuditLogEntry> findByTarget(String target);
}
