package com.brutecx.docflow_backend.audit.onboarding;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;

import java.time.Instant;
import java.util.UUID;

public record OnboardingCanonicalInput(
        Instant timestamp,
        UUID actorUserId,
        String subjectId,
        UUID tenantId,
        UUID inviteId,
        AuditResult result,
        OnboardingOutcome outcome,
        String reasonCode,
        String reasonDetail,
        String correlationId,
        String eventFingerprint
) {

    public static OnboardingCanonicalInput fromEvent(OnboardingAuditEvent e) {
        return new OnboardingCanonicalInput(
                e.getTimestamp(),
                e.getActorUserId(),
                e.getSubjectId(),
                e.getTenantId(),
                e.getInviteId(),
                e.getResult(),
                e.getOutcome(),
                e.getReasonCode(),
                e.getReasonDetail(),
                e.getCorrelationId(),
                e.getEventFingerprint()
        );
    }
}
