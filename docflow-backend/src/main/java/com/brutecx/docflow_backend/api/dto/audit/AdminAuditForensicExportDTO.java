package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditMetadata;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Forensic export DTO:
 * - contains chain fields
 * - contains partition key (tenantId)
 * - stable replay-friendly payload for evidence pipelines
 */
public record AdminAuditForensicExportDTO(
        UUID id,
        Instant timestamp,
        UUID tenantId,
        UUID actorUserId,
        String subjectId,
        AdminAuditActionType actionType,
        AuditResult result,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        String ip,
        String userAgent,
        UUID targetUserId,
        AdminAuditMetadata metadata,
        String eventFingerprint,
        int chainVersion,
        String prevEventHash,
        String eventHash
) implements BaseAuditForensicExportDTO  {
    public static AdminAuditForensicExportDTO from(AdminAuditEvent event) {
        return new AdminAuditForensicExportDTO(
                event.getId(),
                event.getTimestamp(),
                event.getTenantId(),
                event.getActorUserId(),
                event.getSubjectId(),
                event.getActionType(),
                event.getResult(),
                event.getCorrelationId(),
                event.getCorrelationSource(),
                event.getExecutionContext(),
                event.getIp(),
                event.getUserAgent(),
                event.getTargetUserId(),
                event.getMetadata(),
                event.getEventFingerprint(),
                event.getChainVersion(),
                event.getPrevEventHash(),
                event.getEventHash()
        );
    }
}
