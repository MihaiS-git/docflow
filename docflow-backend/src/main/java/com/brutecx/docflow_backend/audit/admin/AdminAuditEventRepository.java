package com.brutecx.docflow_backend.audit.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface AdminAuditEventRepository
        extends JpaRepository<AdminAuditEvent, UUID>,
        JpaSpecificationExecutor<AdminAuditEvent>
{

    @Query("select min(e.timestamp) from AdminAuditEvent e")
    Instant findEarliestTimestamp();
}
