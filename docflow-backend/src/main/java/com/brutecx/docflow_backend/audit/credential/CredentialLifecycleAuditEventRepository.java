package com.brutecx.docflow_backend.audit.credential;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface CredentialLifecycleAuditEventRepository
        extends JpaRepository<CredentialLifecycleAuditEvent, UUID>,
        JpaSpecificationExecutor<CredentialLifecycleAuditEvent> {

    Optional<CredentialLifecycleAuditEvent>
    findTopBySubjectExternalIdOrderByTimestampDescIdDesc(String subjectExternalId);
}