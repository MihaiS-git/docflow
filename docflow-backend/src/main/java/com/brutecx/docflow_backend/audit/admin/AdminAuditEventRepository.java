package com.brutecx.docflow_backend.audit.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, UUID> {
    Page<AdminAuditEvent> findByTimestampBetween(
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<AdminAuditEvent> findByCorrelationId(
            String correlationId,
            Pageable pageable
    );

    Page<AdminAuditEvent> findByActorUserId(
            UUID actorUserId,
            Pageable pageable
    );

    Page<AdminAuditEvent> findByTenantId(
            UUID tenantId,
            Pageable pageable
    );

    Page<AdminAuditEvent> findByTenantIdAndActionTypeIn(
            UUID tenantId,
            EnumSet<AdminAuditActionType> tenantActions,
            Pageable pageable
    );
}
