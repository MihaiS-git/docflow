package com.brutecx.docflow_backend.audit;

public record AuditRequestContext(
        String requestId,
        String subjectId,
        String ip,
        String userAgent
) {
}
