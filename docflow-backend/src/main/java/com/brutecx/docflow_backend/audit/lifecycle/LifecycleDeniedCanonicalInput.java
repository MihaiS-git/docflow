package com.brutecx.docflow_backend.audit.lifecycle;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;

public record LifecycleDeniedCanonicalInput(
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
        String eventFingerprint
) {
    public static LifecycleDeniedCanonicalInput fromEvent(LifecycleDeniedAuditEvent e) {
        return new LifecycleDeniedCanonicalInput(
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
                e.getEventFingerprint()
        );
    }
}
