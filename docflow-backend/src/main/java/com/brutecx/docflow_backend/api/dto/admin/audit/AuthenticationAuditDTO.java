package com.brutecx.docflow_backend.api.dto.admin.audit;

import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventSource;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;

import java.time.Instant;

public record AuthenticationAuditDTO(
        Instant timestamp,
        AuthenticationEventSource source,
        String username,
        AuthenticationResult result,
        String idp,
        String ip,
        String userAgent,
        String correlationId,
        String eventFingerprint
) {

    public static AuthenticationAuditDTO from(AuthenticationEvent event) {
        return new AuthenticationAuditDTO(
                event.getTimestamp(),
                event.getSource(),
                event.getUsername(),
                event.getResult(),
                event.getIdp(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getEventFingerprint()
        );
    }
}
