package com.brutecx.docflow_backend.audit.unauth;

public interface IUnauthenticatedAccessAuditService {
    void record(
            String requestId,
            String httpMethod,
            String path,
            String ip,
            String userAgent
    );
}
