package com.brutecx.docflow_backend.domain.audit.export;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditExportSnapshotRepository
        extends JpaRepository<AuditExportSnapshot, UUID>, JpaSpecificationExecutor<AuditExportSnapshot> {
}
