package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;

import java.time.Instant;
import java.util.UUID;

public record RbacDeniedAuditForensicExportDTO(
        UUID id,
        Instant timestamp,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,
        String subjectId,
        String httpMethod,
        String path,
        String ip,
        String userAgent,
        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash
) implements BaseAuditForensicExportDTO {

    public static RbacDeniedAuditForensicExportDTO from(RbacDeniedAuditEvent e) {
        return new RbacDeniedAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getSubjectId(),
                e.getHttpMethod(),
                e.getPath(),
                e.getIp(),
                e.getUserAgent(),
                e.getEventFingerprint(),
                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
