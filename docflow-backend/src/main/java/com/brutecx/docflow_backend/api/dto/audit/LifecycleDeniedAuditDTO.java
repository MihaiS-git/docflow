package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

public record LifecycleDeniedAuditDTO(
        UUID id,
        Instant timestamp,

        String subjectId,
        String reasonCode,

        String httpMethod,
        String path,
        String ip,
        String userAgent,

        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,

        String eventFingerprint,

        int chainVersion,
        String prevEventHash,
        String eventHash
) implements BaseAuditDTO {

    public static LifecycleDeniedAuditDTO from(LifecycleDeniedAuditEvent e) {
        return new LifecycleDeniedAuditDTO(
                e.getId(),
                e.getTimestamp(),
                e.getSubjectId(),
                e.getReasonCode(),
                e.getHttpMethod(),
                e.getPath(),
                e.getIp(),
                e.getUserAgent(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
