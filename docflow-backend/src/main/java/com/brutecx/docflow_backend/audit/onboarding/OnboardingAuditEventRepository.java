package com.brutecx.docflow_backend.audit.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OnboardingAuditEventRepository
        extends JpaRepository<OnboardingAuditEvent, UUID> {

    boolean existsByInviteId(UUID inviteId);
}
