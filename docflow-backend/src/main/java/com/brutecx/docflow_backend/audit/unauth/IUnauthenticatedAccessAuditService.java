package com.brutecx.docflow_backend.audit.unauth;

public interface IUnauthenticatedAccessAuditService {
    void record(
            String correlationId,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    );
}
