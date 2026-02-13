package com.brutecx.docflow_backend.audit.credential;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.UUID;

public interface CredentialLifecycleAuditEventRepository
        extends JpaRepository<CredentialLifecycleAuditEvent, UUID>,
        JpaSpecificationExecutor<CredentialLifecycleAuditEvent> {

    Page<CredentialLifecycleAuditEvent> findByTimestampBetween(
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<CredentialLifecycleAuditEvent> findByCorrelationId(
            String correlationId,
            Pageable pageable
    );

    Page<CredentialLifecycleAuditEvent> findBySubjectExternalId(
            String subjectExternalId,
            Pageable pageable
    );
}
