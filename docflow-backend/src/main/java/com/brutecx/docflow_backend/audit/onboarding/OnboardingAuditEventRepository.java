package com.brutecx.docflow_backend.audit.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface OnboardingAuditEventRepository
        extends JpaRepository<OnboardingAuditEvent, UUID>,
        JpaSpecificationExecutor<OnboardingAuditEvent> {

    Optional<OnboardingAuditEvent> findTopByTenantIdOrderByTimestampDescIdDesc(UUID tenantId);
}