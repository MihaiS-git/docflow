package com.brutecx.docflow_backend.security.audit.lifecycle;

public interface ILifecycleDeniedAuditService {

    void record(
            String requestId,
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String ip,
            String userAgent
    );

}
