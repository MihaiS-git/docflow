package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventSource;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationAuditDTO(
        UUID id,
        Instant timestamp,
        AuthenticationEventSource source,
        String username,
        String subjectId,
        AuthenticationResult result,
        String idp,
        String ip,
        String userAgent,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult auditResult,
        String eventFingerprint
) {

    public static AuthenticationAuditDTO from(AuthenticationEvent event) {
        return new AuthenticationAuditDTO(
                event.getId(),
                event.getTimestamp(),
                event.getSource(),
                event.getUsername(),
                event.getSubjectId(),
                event.getResult(),
                event.getIdp(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getCorrelationSource(),
                event.getExecutionContext(),
                event.getAuditResult(),
                event.getEventFingerprint()
        );
    }
}
