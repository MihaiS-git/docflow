package com.brutecx.docflow_backend.audit;

public record AuditRequestContext(
        String correlationId,
        String subjectId,
        String ip,
        String userAgent,
        String resourcePath
) {
}
