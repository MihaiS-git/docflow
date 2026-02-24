package com.brutecx.docflow_backend.audit.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface LifecycleDeniedAuditEventRepository
        extends JpaRepository<LifecycleDeniedAuditEvent, UUID>,
        JpaSpecificationExecutor<LifecycleDeniedAuditEvent> {

    @Query("select min(e.timestamp) from LifecycleDeniedAuditEvent e")
    Instant findEarliestTimestamp();
}
