package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

public record AuthenticationCanonicalInput(
        long timestampEpochMs,
        AuthenticationEventSource source,
        String username,
        String subjectId,
        AuthenticationResult result,
        String idp,
        String ip,
        String userAgent,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult auditResult,
        String eventFingerprint
) {
}
