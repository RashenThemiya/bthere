package com.jobhub.repository.audit;

import com.jobhub.entity.audit.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findAllByActionContainingIgnoreCaseAndEntityTypeContainingIgnoreCase(
            String action, String entityType, Pageable pageable);
}
