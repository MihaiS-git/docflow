package com.brutecx.docflow_backend.audit.admin;

import java.util.UUID;

public interface IAdminAuditEventService {
    void record(
            AdminAuditActionType actionType,
            UUID tenantId,
            String subjectId,
            UUID targetUserId,
            AdminAuditMetadata metadata
    );
}
