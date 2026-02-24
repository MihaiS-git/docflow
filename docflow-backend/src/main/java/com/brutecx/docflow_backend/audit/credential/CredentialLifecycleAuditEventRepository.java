package com.brutecx.docflow_backend.audit.credential;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface CredentialLifecycleAuditEventRepository
        extends JpaRepository<CredentialLifecycleAuditEvent, UUID>,
        JpaSpecificationExecutor<CredentialLifecycleAuditEvent> {

    @Query("select min(e.timestamp) from CredentialLifecycleAuditEvent e")
    Instant findEarliestTimestamp();
}
