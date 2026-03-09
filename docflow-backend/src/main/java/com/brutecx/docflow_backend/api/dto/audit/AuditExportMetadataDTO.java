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
        String digestAlgorithm,
        String signatureInput,
        UUID snapshotId,
        Instant createdAt
) {

    public static final String DIGEST_ALG_SHA256 = "SHA-256";

    /**
     * Defines what the signature covers.
     * For DocFlow exports the signature is calculated over the raw JSONL payload bytes.
     */
    public static final String SIGNATURE_INPUT_PAYLOAD_BYTES = "payload_jsonl_bytes";
}