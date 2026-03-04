package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "audit_signing_keys",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_audit_signing_keys_fingerprint",
                        columnNames = {"fingerprint_sha256_hex"}
                )
        },
        indexes = {
                @Index(name = "idx_audit_signing_keys_expires_at", columnList = "expires_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditSigningKey {

    @Id
    @Column(name = "key_id", nullable = false, length = 128, updatable = false)
    private String keyId;

    @Column(name = "public_key_pem", nullable = false, updatable = false, columnDefinition = "text")
    private String publicKeyPem;

    @Column(name = "encrypted_private_key", nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] encryptedPrivateKey;

    @Column(name = "fingerprint_sha256_hex", nullable = false, length = 64, updatable = false)
    private String fingerprintSha256Hex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    private AuditSigningKey(
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

    public static AuditSigningKey create(
            String keyId,
            String publicKeyPem,
            byte[] encryptedPrivateKey,
            String fingerprintSha256Hex,
            int maxAgeDays
    ) {
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plus(maxAgeDays, ChronoUnit.DAYS);

        return new AuditSigningKey(
                keyId,
                publicKeyPem,
                encryptedPrivateKey,
                fingerprintSha256Hex,
                createdAt,
                expiresAt,
                true
        );
    }

    public byte[] getEncryptedPrivateKey() {
        return encryptedPrivateKey == null ? null : encryptedPrivateKey.clone();
    }

}