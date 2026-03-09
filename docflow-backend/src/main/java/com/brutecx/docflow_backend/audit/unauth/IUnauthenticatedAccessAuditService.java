package com.brutecx.docflow_backend.audit.unauth;

public interface IUnauthenticatedAccessAuditService {
    void record(
            String httpMethod,
            String path
    );
}

