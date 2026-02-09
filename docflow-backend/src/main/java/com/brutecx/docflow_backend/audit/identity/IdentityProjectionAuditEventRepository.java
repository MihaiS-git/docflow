package com.brutecx.docflow_backend.audit.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IdentityProjectionAuditEventRepository extends JpaRepository<IdentityProjectionAuditEvent, UUID> {
}
