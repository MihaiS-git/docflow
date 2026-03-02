package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditExportVerificationResultDTO(
        boolean ok,
        UUID snapshotId,
        String stream,
        UUID tenantId,
        Instant exportFrom,
        Instant exportTo,
        long expectedRowCount,
        String expectedPayloadSha256Hex,
        String computedPayloadSha256Hex,
        boolean digestMatches,
        boolean keyIdExists,
        boolean signatureValid,
        String signatureAlgorithm,
        String keyId,
        boolean metaPresent,
        boolean metaParsed,
        boolean metaSnapshotIdMatches,
        boolean metaDigestMatches,
        boolean metaSignatureMatches,
        boolean metaKeyIdMatches,
        boolean metaAlgorithmMatches,
        boolean metaDigestAlgorithmMatches,
        boolean metaSignatureInputMatches,
        boolean metaStreamMatches,
        boolean metaRangeMatches,
        boolean metaTenantMatches,
        boolean metaRowCountMatches,
        String message
) {
}
