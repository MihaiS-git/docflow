package com.brutecx.docflow_backend.audit.sensitive;

import java.util.UUID;

public interface ISensitiveAccessAuditService {

    void record(
            UUID actorUserId,
            String actorExternalSubjectId,
            UUID tenantId,
            SensitiveAccessSubjectType subjectType,
            String subjectId,
            String resource,
            String action,
            String resourcePath,
            String correlationId,
            String ip,
            String userAgent,
            String reasonCode,
            String reasonDetail,
            SensitiveDataClassification dataClassification,
            String eventFingerprint
    );
}
