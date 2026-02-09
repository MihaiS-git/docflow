package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingOutcome;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

public record OnboardingAuditDTO(
        Instant timestamp,
        UUID actorUserId,
        String subjectId,
        UUID tenantId,
        UUID inviteId,
        AuditResult result,
        OnboardingOutcome outcome,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        String ip,
        String userAgent,
        String reasonCode,
        String reasonDetail,
        String eventFingerprint
) {

    public static OnboardingAuditDTO from(OnboardingAuditEvent event) {
        return new OnboardingAuditDTO(
                event.getTimestamp(),
                event.getActorUserId(),
                event.getSubjectId(),
                event.getTenantId(),
                event.getInviteId(),
                event.getResult(),
                event.getOutcome(),
                event.getCorrelationId(),
                event.getCorrelationSource(),
                event.getExecutionContext(),
                event.getIp(),
                event.getUserAgent(),
                event.getReasonCode(),
                event.getReasonDetail(),
                event.getEventFingerprint()
        );
    }
}
