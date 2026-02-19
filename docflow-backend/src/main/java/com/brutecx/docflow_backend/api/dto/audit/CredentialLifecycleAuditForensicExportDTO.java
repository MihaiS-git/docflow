package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleEventType;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Forensic export DTO for the credential lifecycle audit stream.
 * - stable schema (do not serialize entities)
 * - includes tamper-evidence fields
 */
public record CredentialLifecycleAuditForensicExportDTO(
        UUID id,
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
        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash
) implements BaseAuditForensicExportDTO {

    public static CredentialLifecycleAuditForensicExportDTO from(CredentialLifecycleAuditEvent e) {
        return new CredentialLifecycleAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),
                e.getSubjectExternalId(),
                e.getClientId(),
                e.getSessionId(),
                e.getIp(),
                e.getEventType(),
                e.getRequiredAction(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getReasonCode(),
                e.getReasonDetail(),
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
