package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventSource;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationAuditForensicExportDTO(
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
        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash
) {
    public static AuthenticationAuditForensicExportDTO from(AuthenticationEvent e) {
        return new AuthenticationAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),
                e.getSource(),
                e.getUsername(),
                e.getSubjectId(),
                e.getResult(),
                e.getIdp(),
                e.getIp(),
                e.getUserAgent(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getAuditResult(),
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
