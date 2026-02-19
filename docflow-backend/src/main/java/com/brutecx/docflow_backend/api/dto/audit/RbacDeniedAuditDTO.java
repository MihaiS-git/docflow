package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;

import java.time.Instant;
import java.util.UUID;

public record RbacDeniedAuditDTO(
        UUID id,
        Instant timestamp,

        String subjectId,

        String httpMethod,
        String path,
        String ip,
        String userAgent,

        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,

        String eventFingerprint,

        int chainVersion,
        String prevEventHash,
        String eventHash
) implements BaseAuditDTO {

    public static RbacDeniedAuditDTO from(RbacDeniedAuditEvent event) {
        return new RbacDeniedAuditDTO(
                event.getId(),
                event.getTimestamp(),

                event.getSubjectId(),

                event.getHttpMethod(),
                event.getPath(),
                event.getIp(),
                event.getUserAgent(),

                event.getCorrelationId(),
                event.getCorrelationSource(),
                event.getExecutionContext(),
                event.getResult(),

                event.getEventFingerprint(),

                event.getChainVersion(),
                event.getPrevEventHash(),
                event.getEventHash()
        );
    }
}
