package com.brutecx.docflow_backend.audit.rbac;

public interface IRbacDeniedAuditService {
    void record(
            String subjectId,
            String httpMethod,
            String path,
            String eventFingerprint
    );
}
