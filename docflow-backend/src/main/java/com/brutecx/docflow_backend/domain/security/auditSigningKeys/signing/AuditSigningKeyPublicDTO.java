package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

import java.time.Instant;

public record AuditSigningKeyPublicDTO(
        String keyId,
        String fingerprintSha256Hex,
        Instant createdAt
) {
    public static AuditSigningKeyPublicDTO from(AuditSigningKey key) {
        return new AuditSigningKeyPublicDTO(
                key.getKeyId(),
                key.getFingerprintSha256Hex(),
                key.getCreatedAt()
        );
    }
}