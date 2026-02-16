package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

public record LifecycleDeniedAuditForensicExportDTO(
        UUID id,
        Instant timestamp,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,
        String subjectId,
        String reasonCode,
        String httpMethod,
        String path,
        String ip,
        String userAgent,
        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash
) {
    public static LifecycleDeniedAuditForensicExportDTO from(LifecycleDeniedAuditEvent e) {
        return new LifecycleDeniedAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getSubjectId(),
                e.getReasonCode(),
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
