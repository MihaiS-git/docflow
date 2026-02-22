package com.brutecx.docflow_backend.domain.security;

public record AuditSigningKeyPublicDTO(
        String keyId,
        String fingerprintSha256Hex,
        String createdAt
) {
    public static AuditSigningKeyPublicDTO from(AuditSigningKey key) {
        return new AuditSigningKeyPublicDTO(
                key.getKeyId(),
                key.getFingerprintSha256Hex(),
                key.getCreatedAt().toString()
        );
    }
}