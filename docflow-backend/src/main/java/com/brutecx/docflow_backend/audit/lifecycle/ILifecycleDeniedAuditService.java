package com.brutecx.docflow_backend.audit.lifecycle;

public interface ILifecycleDeniedAuditService {

    void record(
            String correlationId,
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    );

}
