package com.brutecx.docflow_backend.audit.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface IdentityProjectionAuditEventRepository
        extends JpaRepository<IdentityProjectionAuditEvent, UUID>,
        JpaSpecificationExecutor<IdentityProjectionAuditEvent> {

    Optional<IdentityProjectionAuditEvent> findTopBySubjectIdOrderByTimestampDescIdDesc(String subjectId);
}