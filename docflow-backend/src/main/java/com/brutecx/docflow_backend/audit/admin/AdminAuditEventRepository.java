package com.brutecx.docflow_backend.audit.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, UUID> {
}
