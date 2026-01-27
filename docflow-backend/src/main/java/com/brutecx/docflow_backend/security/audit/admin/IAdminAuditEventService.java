package com.brutecx.docflow_backend.security.audit.admin;

import java.util.UUID;

public interface IAdminAuditEventService {
    void record(
            UUID actorUserId,
            UUID tenantId,
            AdminAuditActionType actionType,
            UUID targetUserId,
            AdminAuditMetadata metadata
    );
}
