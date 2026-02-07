package com.brutecx.docflow_backend.api.dto.admin.audit;

import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessAuditEvent;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;

import java.time.Instant;
import java.util.UUID;

public record SensitiveAccessAuditDTO(
        Instant timestamp,

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
) {

    public static SensitiveAccessAuditDTO from(SensitiveAccessAuditEvent event) {
        return new SensitiveAccessAuditDTO(
                event.getTimestamp(),

                event.getActorUserId(),
                event.getActorExternalSubjectId(),
                event.getTenantId(),

                event.getSubjectType(),
                event.getSubjectId(),
                event.getResource(),
                event.getAction(),
                event.getResourcePath(),

                event.getCorrelationId(),
                event.getIp(),
                event.getUserAgent(),

                event.getReasonCode(),
                event.getReasonDetail(),
                event.getDataClassification(),

                event.getEventFingerprint()
        );
    }
}
