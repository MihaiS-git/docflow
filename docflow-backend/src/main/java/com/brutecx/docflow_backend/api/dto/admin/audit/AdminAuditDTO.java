package com.brutecx.docflow_backend.api.dto.admin.audit;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditMetadata;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditDTO(
        Instant timestamp,
        UUID actorUserId,
        String subjectId,
        UUID tenantId,
        AdminAuditActionType actionType,
        UUID targetUserId,
        AdminAuditMetadata metadata,
        String ip,
        String userAgent,
        String correlationId,
        String eventFingerprint
) {

    public static AdminAuditDTO from(AdminAuditEvent event) {
        return new AdminAuditDTO(
                event.getTimestamp(),
                event.getActorUserId(),
                event.getSubjectId(),
                event.getTenantId(),
                event.getActionType(),
                event.getTargetUserId(),
                event.getMetadata(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getEventFingerprint()
        );
    }
}
