package com.brutecx.docflow_backend.audit.unauth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface UnauthenticatedAccessAuditEventRepository
        extends JpaRepository<UnauthenticatedAccessAuditEvent, UUID>,
        JpaSpecificationExecutor<UnauthenticatedAccessAuditEvent> {

    @Query("select min(e.timestamp) from UnauthenticatedAccessAuditEvent e")
    Instant findEarliestTimestamp();
}

