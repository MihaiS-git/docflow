package com.brutecx.docflow_backend.audit.credential;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CredentialLifecycleAuditEventRepository
        extends JpaRepository<CredentialLifecycleAuditEvent, UUID>,
        JpaSpecificationExecutor<CredentialLifecycleAuditEvent> {

}
