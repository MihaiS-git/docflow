package com.brutecx.docflow_backend.domain.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_signing_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditSigningKey {

    @Id
    @Column(name = "key_id", nullable = false, length = 128, updatable = false)
    private String keyId;

    @Lob
    @Column(name = "public_key_pem", nullable = false, updatable = false)
    private String publicKeyPem;

    /**
     * AES-256-GCM encrypted PKCS8 DER bytes.
     * Format is application-defined by AuditKeyCrypto (versioned blob).
     */
    @Lob
    @Column(name = "encrypted_private_key", nullable = false, updatable = false)
    private byte[] encryptedPrivateKey;

    @Column(name = "fingerprint_sha256_hex", nullable = false, length = 64, updatable = false)
    private String fingerprintSha256Hex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    public AuditSigningKey(
            String keyId,
            String publicKeyPem,
            byte[] encryptedPrivateKey,
            String fingerprintSha256Hex,
            Instant createdAt,
            Instant expiresAt,
            boolean active
    ) {
        this.keyId = keyId;
        this.publicKeyPem = publicKeyPem;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.fingerprintSha256Hex = fingerprintSha256Hex;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}