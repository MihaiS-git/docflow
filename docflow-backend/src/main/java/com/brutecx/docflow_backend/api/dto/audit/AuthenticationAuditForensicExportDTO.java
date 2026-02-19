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

        AuthenticationResult authenticationResult, // domain result

        String idp,
        String ip,
        String userAgent,

        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,

        AuditResult result, // audit-layer result (required by BaseAuditDTO)

        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash

) implements BaseAuditForensicExportDTO {

    public static AuthenticationAuditForensicExportDTO from(AuthenticationEvent e) {
        return new AuthenticationAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),
                e.getSource(),
                e.getUsername(),
                e.getSubjectId(),
                e.getAuthenticationResult(),            // domain result
                e.getIdp(),
                e.getIp(),
                e.getUserAgent(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),       // audit-layer result
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
