package com.brutecx.docflow_backend.api.dto.admin.audit;

import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingOutcome;

import java.time.Instant;
import java.util.UUID;

public record OnboardingAuditDTO(
        Instant timestamp,
        UUID actorUserId,
        String subjectId,
        UUID tenantId,
        UUID inviteId,
        OnboardingOutcome outcome,
        String failureReason,
        String correlationId,
        String ip,
        String userAgent,
        String eventFingerprint
) {

    public static OnboardingAuditDTO from(OnboardingAuditEvent event) {
        return new OnboardingAuditDTO(
                event.getTimestamp(),
                event.getActorUserId(),
                event.getSubjectId(),
                event.getTenantId(),
                event.getInviteId(),
                event.getOutcome(),
                event.getFailureReason(),
                event.getCorrelationId(),
                event.getIp(),
                event.getUserAgent(),
                event.getEventFingerprint()
        );
    }
}
