package com.brutecx.docflow_backend.audit.sensitive;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface SensitiveAccessAuditEventRepository
        extends JpaRepository<SensitiveAccessAuditEvent, UUID>,
        JpaSpecificationExecutor<SensitiveAccessAuditEvent> {
    Page<SensitiveAccessAuditEvent> findByTimestampBetween(
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<SensitiveAccessAuditEvent> findByCorrelationId(
            String correlationId,
            Pageable pageable
    );

    Page<SensitiveAccessAuditEvent> findBySubjectId(
            String subjectId,
            Pageable pageable
    );

    Page<SensitiveAccessAuditEvent> findByTenantId(
            UUID tenantId,
            Pageable pageable
    );

    Page<SensitiveAccessAuditEvent> findByActorUserId(
            UUID actorUserId,
            Pageable pageable
    );

}
