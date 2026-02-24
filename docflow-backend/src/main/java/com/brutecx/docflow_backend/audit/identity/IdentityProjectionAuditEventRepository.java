package com.brutecx.docflow_backend.audit.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface IdentityProjectionAuditEventRepository
        extends JpaRepository<IdentityProjectionAuditEvent, UUID>,
        JpaSpecificationExecutor<IdentityProjectionAuditEvent> {

    @Query("select min(e.timestamp) from IdentityProjectionAuditEvent e")
    Instant findEarliestTimestamp();
}
