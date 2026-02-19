package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessAuditEvent;

import java.time.Instant;
import java.util.UUID;

public record UnauthenticatedAccessAuditForensicExportDTO(

        UUID id,
        Instant timestamp,

        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,

        String httpMethod,
        String path,
        String ip,
        String userAgent,

        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash

) implements BaseAuditForensicExportDTO {

    public static UnauthenticatedAccessAuditForensicExportDTO from(
            UnauthenticatedAccessAuditEvent e
    ) {
        return new UnauthenticatedAccessAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getHttpMethod(),
                e.getPath(),
                e.getIp(),
                e.getUserAgent(),
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
