package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.identity.IdentityProjectionAuditEvent;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

public record IdentityProjectionAuditForensicExportDTO(

        UUID id,
        Instant timestamp,

        String subjectId,

        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,

        String reasonCode,

        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash

) implements BaseAuditForensicExportDTO {

    public static IdentityProjectionAuditForensicExportDTO from(
            IdentityProjectionAuditEvent e
    ) {
        return new IdentityProjectionAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),
                e.getSubjectId(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getReasonCode(),
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
