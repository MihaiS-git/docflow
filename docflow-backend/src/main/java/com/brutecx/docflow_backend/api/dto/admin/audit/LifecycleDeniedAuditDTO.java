package com.brutecx.docflow_backend.api.dto.admin.audit;

import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;

import java.time.Instant;

public record LifecycleDeniedAuditDTO(
        Instant timestamp,
        String subjectId,
        String reasonCode,
        String httpMethod,
        String path,
        String ip,
        String userAgent,
        String correlationId,
        String eventFingerprint
) {

    public static LifecycleDeniedAuditDTO from(LifecycleDeniedAuditEvent event) {
        return new LifecycleDeniedAuditDTO(
                event.getTimestamp(),
                event.getSubjectId(),
                event.getReasonCode(),
                event.getHttpMethod(),
                event.getPath(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getEventFingerprint()
        );
    }
}
