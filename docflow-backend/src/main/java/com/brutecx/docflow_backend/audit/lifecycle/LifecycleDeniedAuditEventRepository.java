package com.brutecx.docflow_backend.audit.lifecycle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface LifecycleDeniedAuditEventRepository extends JpaRepository<LifecycleDeniedAuditEvent, UUID> {
    Page<LifecycleDeniedAuditEvent> findByCorrelationId(String correlationId, Pageable pageable);

    Page<LifecycleDeniedAuditEvent> findBySubjectId(String subjectId, Pageable pageable);

    Page<LifecycleDeniedAuditEvent> findByTimestampBetween(Instant from, Instant to, Pageable pageable);
}
