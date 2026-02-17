package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.identity.IdentityProjectionAuditEvent;

import java.time.Instant;
import java.util.UUID;

public record IdentityProjectionAuditDTO(
        UUID id,
        Instant timestamp,
        String subjectId,
        String correlationId,
        String executionContext,
        String correlationSource,
        String result,
        String reasonCode,
        String eventFingerprint,
        int chainVersion,
        String prevHash,
        String eventHash
) {

    public static IdentityProjectionAuditDTO from(IdentityProjectionAuditEvent e) {
        return new IdentityProjectionAuditDTO(
                e.getId(),
                e.getTimestamp(),
                e.getSubjectId(),
                e.getCorrelationId(),
                e.getExecutionContext() != null ? e.getExecutionContext().name() : null,
                e.getCorrelationSource() != null ? e.getCorrelationSource().name() : null,
                e.getResult() != null ? e.getResult().name() : null,
                e.getReasonCode(),
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevHash(),
                e.getEventHash()
        );
    }
}
