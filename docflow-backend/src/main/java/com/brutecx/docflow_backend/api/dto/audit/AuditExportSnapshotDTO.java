package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.domain.audit.export.AuditExportSnapshot;

import java.time.Instant;
import java.util.UUID;

public record AuditExportSnapshotDTO(
        UUID id,
        String stream,
        Instant fromTs,
        Instant toTs,
        UUID tenantId,
        String sha256DigestHex,
        long rowCount,
        Instant createdAt,
        UUID createdBy,
        String signatureB64,
        String signatureAlg,
        String keyId
) {
    public static AuditExportSnapshotDTO from(AuditExportSnapshot s) {
        return new AuditExportSnapshotDTO(
                s.getId(),
                s.getStream(),
                s.getFromTs(),
                s.getToTs(),
                s.getTenantId(),
                s.getSha256DigestHex(),
                s.getRowCount(),
                s.getCreatedAt(),
                s.getCreatedBy(),
                s.getSignatureB64(),
                s.getSignatureAlg(),
                s.getKeyId()
        );
    }
}
