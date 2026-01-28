package com.brutecx.docflow_backend.security.audit.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LifecycleDeniedAuditEventRepository extends JpaRepository<LifecycleDeniedAuditEvent, UUID> {
}
