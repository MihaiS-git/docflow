package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessAuditEvent;
import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessCanonicalMaterialBuilder;

import java.time.Instant;
import java.util.UUID;

public record UnauthenticatedAccessAuditForensicExportDTO(
        String stream,
        UUID id,
        Instant timestamp,
        String correlationId,
        String correlationSource,
        String executionContext,
        String result,
        String httpMethod,
        String path,
        String ip,
        String userAgent,
        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash
) {

    public static UnauthenticatedAccessAuditForensicExportDTO from(
            UnauthenticatedAccessAuditEvent e
    ) {
        return new UnauthenticatedAccessAuditForensicExportDTO(
                UnauthenticatedAccessCanonicalMaterialBuilder.STREAM,
                e.getId(),
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource().name(),
                e.getExecutionContext().name(),
                e.getResult().name(),
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
