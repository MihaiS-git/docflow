package com.brutecx.docflow_backend.audit.unauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UnauthenticatedAccessAuditEventRepository extends JpaRepository<UnauthenticatedAccessAuditEvent, UUID> {

}
