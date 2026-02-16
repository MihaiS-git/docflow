package com.brutecx.docflow_backend.audit.rbac;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;

public record RbacDeniedCanonicalInput(
        Instant timestamp,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,
        String subjectId,
        String httpMethod,
        String path,
        String ip,
        String userAgent,
        String eventFingerprint
) {
    public static RbacDeniedCanonicalInput fromEvent(RbacDeniedAuditEvent e) {
        return new RbacDeniedCanonicalInput(
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getSubjectId(),
                e.getHttpMethod(),
                e.getPath(),
                e.getIp(),
                e.getUserAgent(),
                e.getEventFingerprint()
        );
    }
}
