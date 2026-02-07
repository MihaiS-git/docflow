package com.brutecx.docflow_backend.audit.onboarding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface OnboardingAuditEventRepository
        extends JpaRepository<OnboardingAuditEvent, UUID> {

    boolean existsByInviteId(UUID inviteId);

    Page<OnboardingAuditEvent> findByTimestampBetween(
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<OnboardingAuditEvent> findByCorrelationId(
            String correlationId,
            Pageable pageable
    );

    Page<OnboardingAuditEvent> findBySubjectId(
            String subjectId,
            Pageable pageable
    );

    Page<OnboardingAuditEvent> findByTenantId(
            UUID tenantId,
            Pageable pageable
    );

}
