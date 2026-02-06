package com.brutecx.docflow_backend.audit.rbac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RbacDeniedAuditEventRepository extends JpaRepository<RbacDeniedAuditEvent, UUID> {
}
