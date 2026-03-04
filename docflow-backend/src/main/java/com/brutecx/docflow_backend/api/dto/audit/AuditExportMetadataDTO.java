package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditExportMetadataDTO(
        int version,
        String stream,
        Instant from,
        Instant to,
        UUID tenantId,
        long rowCount,
        String payloadSha256Hex,
        String signatureB64,
        String signatureAlgorithm,
        String keyId,
        String publicKeyFingerprint,
        String publicKeyPem,
        String digestAlgorithm,
        String signatureInput,
        UUID snapshotId,
        Instant createdAt
) {
    public static final String DIGEST_ALG_SHA256 = "SHA-256";

    /**
     * Industry standard:
     * - signatureAlgorithm = SHA256withRSA
     * - signatureInput = raw payload JSONL bytes (payload only, excluding meta line)
     */
    public static final String SIGNATURE_INPUT_PAYLOAD_JSONL_BYTES = "payload_jsonl_bytes";
}