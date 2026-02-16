package com.brutecx.docflow_backend.audit.unauth;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;

public record UnauthenticatedAccessCanonicalInput(
        Instant timestamp,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,
        String httpMethod,
        String path,
        String ip,
        String userAgent,
        String eventFingerprint
) {
    public static UnauthenticatedAccessCanonicalInput fromEvent(
            UnauthenticatedAccessAuditEvent e
    ) {
        return new UnauthenticatedAccessCanonicalInput(
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getHttpMethod(),
                e.getPath(),
                e.getIp(),
                e.getUserAgent(),
                e.getEventFingerprint()
        );
    }
}
