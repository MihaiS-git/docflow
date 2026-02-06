package com.brutecx.docflow_backend.audit.rbac;

public interface IRbacDeniedAuditService {
    void record(
            String requestId,
            String subjectId,
            String httpMethod,
            String path,
            String ip,
            String userAgent
    );
}
