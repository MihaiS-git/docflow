package com.brutecx.docflow_backend.audit.rbac;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface RbacDeniedAuditEventRepository
        extends JpaRepository<RbacDeniedAuditEvent, UUID>,
        JpaSpecificationExecutor<RbacDeniedAuditEvent> {
    Page<RbacDeniedAuditEvent> findByCorrelationId(String correlationId, Pageable pageable);

    Page<RbacDeniedAuditEvent> findBySubjectId(String subjectId, Pageable pageable);

    Page<RbacDeniedAuditEvent> findByTimestampBetween(Instant from, Instant to, Pageable pageable);
}

