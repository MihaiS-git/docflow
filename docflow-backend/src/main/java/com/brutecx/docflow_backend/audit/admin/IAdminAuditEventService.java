package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.EventFingerprint;

import java.util.UUID;

public interface IAdminAuditEventService {
    void record(
            UUID actorUserId,
            String ip,
            String userAgent,
            String correlationId,
            String subjectId,
            UUID tenantId,
            AdminAuditActionType actionType,
            UUID targetUserId,
            AdminAuditMetadata metadata,
            String eventFingerprint
    );
}
