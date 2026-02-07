package com.brutecx.docflow_backend.api.dto.admin.audit;

import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;

import java.time.Instant;

public record RbacDeniedAuditDTO(
        Instant timestamp,
        String subjectId,
        String httpMethod,
        String path,
        String ip,
        String userAgent,
        String correlationId,
        String eventFingerprint
) {

    public static RbacDeniedAuditDTO from(RbacDeniedAuditEvent event) {
        return new RbacDeniedAuditDTO(
                event.getTimestamp(),
                event.getSubjectId(),
                event.getHttpMethod(),
                event.getPath(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getEventFingerprint()
        );
    }
}
