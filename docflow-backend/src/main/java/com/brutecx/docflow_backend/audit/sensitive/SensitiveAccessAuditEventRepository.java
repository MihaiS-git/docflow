package com.brutecx.docflow_backend.audit.sensitive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface SensitiveAccessAuditEventRepository
        extends JpaRepository<SensitiveAccessAuditEvent, UUID>,
        JpaSpecificationExecutor<SensitiveAccessAuditEvent> {

    @Query("select min(e.timestamp) from SensitiveAccessAuditEvent e")
    Instant findEarliestTimestamp();
}
