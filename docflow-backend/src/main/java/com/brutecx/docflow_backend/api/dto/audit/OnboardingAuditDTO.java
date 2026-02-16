package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingOutcome;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;

import java.time.Instant;
import java.util.UUID;

public record OnboardingAuditDTO(
        UUID id,
        Instant timestamp,
        UUID actorUserId,
        String subjectId,
        UUID tenantId,
        UUID inviteId,
        String correlationId,
        com.brutecx.docflow_backend.audit.provenance.CorrelationSource correlationSource,
        com.brutecx.docflow_backend.audit.provenance.ExecutionContext executionContext,
        String ip,
        String userAgent,
        AuditResult result,
        OnboardingOutcome outcome,
        String reasonCode,
        String reasonDetail,
        String eventFingerprint
) {

    public static OnboardingAuditDTO from(OnboardingAuditEvent e) {
        return new OnboardingAuditDTO(
                e.getId(),
                e.getTimestamp(),
                e.getActorUserId(),
                e.getSubjectId(),
                e.getTenantId(),
                e.getInviteId(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getIp(),
                e.getUserAgent(),
                e.getResult(),
                e.getOutcome(),
                e.getReasonCode(),
                e.getReasonDetail(),
                e.getEventFingerprint()
        );
    }
}
