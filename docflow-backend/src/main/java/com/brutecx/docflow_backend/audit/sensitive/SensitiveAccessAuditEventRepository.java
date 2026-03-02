package com.brutecx.docflow_backend.audit.sensitive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SensitiveAccessAuditEventRepository
        extends JpaRepository<SensitiveAccessAuditEvent, UUID>,
        JpaSpecificationExecutor<SensitiveAccessAuditEvent> {

}
