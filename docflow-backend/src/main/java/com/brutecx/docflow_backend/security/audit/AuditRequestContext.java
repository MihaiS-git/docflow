package com.brutecx.docflow_backend.security.audit;

public record AuditRequestContext(
        String requestId,
        String subjectId,
        String ip,
        String userAgent
) {
}
