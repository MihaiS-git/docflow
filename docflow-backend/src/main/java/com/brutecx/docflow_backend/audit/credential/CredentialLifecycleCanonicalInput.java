package com.brutecx.docflow_backend.audit.credential;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;

public record CredentialLifecycleCanonicalInput(
        Instant timestamp,
        String subjectExternalId,
        String clientId,
        String sessionId,
        String ip,
        CredentialLifecycleEventType eventType,
        String requiredAction,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,
        String reasonCode,
        String reasonDetail,
        String eventFingerprint
) {

    public static CredentialLifecycleCanonicalInput from(
            CredentialLifecycleAuditEvent event
    ) {
        return new CredentialLifecycleCanonicalInput(
                event.getTimestamp(),
                event.getSubjectExternalId(),
                event.getClientId(),
                event.getSessionId(),
                event.getIp(),
                event.getEventType(),
                event.getRequiredAction(),
                event.getCorrelationId(),
                event.getCorrelationSource(),
                event.getExecutionContext(),
                event.getResult(),
                event.getReasonCode(),
                event.getReasonDetail(),
                event.getEventFingerprint()
        );
    }
}
