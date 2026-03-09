package com.brutecx.docflow_backend.audit.lifecycle;

public interface ILifecycleDeniedAuditService {

    void record(
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            LifecycleAuditMetadata metadata
    );
}
