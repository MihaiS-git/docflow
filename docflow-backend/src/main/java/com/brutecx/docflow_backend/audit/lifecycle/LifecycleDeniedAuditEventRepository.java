package com.brutecx.docflow_backend.audit.lifecycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface LifecycleDeniedAuditEventRepository
        extends JpaRepository<LifecycleDeniedAuditEvent, UUID>,
        JpaSpecificationExecutor<LifecycleDeniedAuditEvent> {

    Optional<LifecycleDeniedAuditEvent> findTopBySubjectIdOrderByTimestampDescIdDesc(String subjectId);
}