package com.brutecx.docflow_backend.audit.unauth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface UnauthenticatedAccessAuditEventRepository
        extends JpaRepository<UnauthenticatedAccessAuditEvent, UUID>,
        JpaSpecificationExecutor<UnauthenticatedAccessAuditEvent> {

}

