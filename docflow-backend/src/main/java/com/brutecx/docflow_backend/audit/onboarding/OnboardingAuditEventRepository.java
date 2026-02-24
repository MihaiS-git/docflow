package com.brutecx.docflow_backend.audit.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface OnboardingAuditEventRepository
        extends JpaRepository<OnboardingAuditEvent, UUID>,
        JpaSpecificationExecutor<OnboardingAuditEvent> {

    @Query("select min(e.timestamp) from OnboardingAuditEvent e")
    Instant findEarliestTimestamp();
}
