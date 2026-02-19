package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessAuditEvent;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;

import java.time.Instant;
import java.util.UUID;

public record SensitiveAccessAuditForensicExportDTO(
        UUID id,
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
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,
        String ip,
        String userAgent,

        String reasonCode,
        String reasonDetail,
        SensitiveDataClassification dataClassification,

        String eventFingerprint,

        int chainVersion,
        String prevEventHash,
        String eventHash
) implements BaseAuditForensicExportDTO {
    public static SensitiveAccessAuditForensicExportDTO from(SensitiveAccessAuditEvent e) {
        return new SensitiveAccessAuditForensicExportDTO(
                e.getId(),
                e.getTimestamp(),

                e.getActorUserId(),
                e.getActorExternalSubjectId(),
                e.getTenantId(),

                e.getSubjectType(),
                e.getSubjectId(),
                e.getResource(),
                e.getAction(),
                e.getResourcePath(),

                e.getCorrelationId(),
                e.getCorrelationSource(),
                e.getExecutionContext(),
                e.getResult(),
                e.getIp(),
                e.getUserAgent(),

                e.getReasonCode(),
                e.getReasonDetail(),
                e.getDataClassification(),

                e.getEventFingerprint(),

                e.getChainVersion(),
                e.getPrevEventHash(),
                e.getEventHash()
        );
    }
}
