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
 * Forensic export DTO (immutable evidence record).
 * Requirements:
 * - separate from UI DTOs
 * - includes chain fields + partition key
 * - stable fields suitable for JSONL export + offline verification
 */
public record AdminAuditForensicDTO(
        // Partition / stream identity
        String stream,
        UUID tenantId,

        // Primary identity & ordering
        UUID id,
        Instant timestamp,

        // Actor / subject / action
        UUID actorUserId,
        String subjectId,
        AdminAuditActionType actionType,
        UUID targetUserId,

        // Provenance
        String ip,
        String userAgent,
        String correlationId,
        CorrelationSource correlationSource,
        ExecutionContext executionContext,
        AuditResult result,

        // Metadata (typed)
        AdminAuditMetadata metadata,

        // Idempotency
        String eventFingerprint,

        // Tamper-evidence
        int chainVersion,
        String prevEventHash,
        String eventHash
) {
    public static AdminAuditForensicDTO from(AdminAuditEvent event, String streamName) {
        return new AdminAuditForensicDTO(
                streamName,
                event.getTenantId(),
                event.getId(),
                event.getTimestamp(),
                event.getActorUserId(),
                event.getSubjectId(),
                event.getActionType(),
                event.getTargetUserId(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getCorrelationSource(),
                event.getExecutionContext(),
                event.getResult(),
                event.getMetadata(),
                event.getEventFingerprint(),
                event.getChainVersion(),
                event.getPrevEventHash(),
                event.getEventHash()
        );
    }
}
