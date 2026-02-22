package com.brutecx.docflow_backend.audit.keyrotation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditExportSigningKeyRotationEventRepository
        extends JpaRepository<AuditExportSigningKeyRotationEvent, UUID>,
        JpaSpecificationExecutor<AuditExportSigningKeyRotationEvent> {
}