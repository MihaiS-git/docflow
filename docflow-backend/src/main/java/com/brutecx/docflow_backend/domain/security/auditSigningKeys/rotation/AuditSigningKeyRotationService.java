package com.brutecx.docflow_backend.domain.security.auditSigningKeys.rotation;

import com.brutecx.docflow_backend.domain.security.auditSigningKeys.crypto.AuditKeyCrypto;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.crypto.RsaKeyCodec;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKeyRepository;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Service
public class AuditSigningKeyRotationService {

    private static final String NAMESPACE = "audit-export";
    private static final String KEY_PREFIX = NAMESPACE + ":v";

    private static final long ROTATION_LOCK_KEY = 884422113377L;

    private final AuditSigningKeyRepository repository;
    private final AuditSigningKeyResolver resolver;
    private final AuditKeyCrypto crypto;
    private final JdbcTemplate jdbcTemplate;

    private final int rsaBits;
    private final int maxAgeDays;

    public AuditSigningKeyRotationService(
            AuditSigningKeyRepository repository,
            AuditSigningKeyResolver resolver,
            AuditKeyCrypto crypto,
            JdbcTemplate jdbcTemplate,
            @Value("${docflow.audit.export.signing.rsa-bits}") int rsaBits,
            @Value("${docflow.audit.signing.rotation.max-age-days}") int maxAgeDays
    ) {
        this.repository = repository;
        this.resolver = resolver;
        this.crypto = crypto;
        this.jdbcTemplate = jdbcTemplate;
        this.rsaBits = rsaBits;
        this.maxAgeDays = maxAgeDays;
    }

    /**
     * Used by export pipeline.
     * Guarantees an active signing key exists.
     */
    @Transactional
    public AuditSigningKey requireActiveForExport() {
        AuditSigningKey current = resolver.findActive().orElse(null);

        if (current != null && !isExpired(current)) {
            return current;
        }

        acquireAdvisoryLock();

        AuditSigningKey afterLock = repository.findActiveForUpdate().orElse(null);

        if (afterLock != null && !isExpired(afterLock)) {
            return afterLock;
        }

        String reason = (afterLock == null) ? "MISSING" : "EXPIRED";
        String oldKeyId = (afterLock == null) ? null : afterLock.getKeyId();

        return rotate(reason, oldKeyId);
    }

    /**
     * Used by scheduler.
     */
    @Transactional
    public void rotateIfRequired() {
        AuditSigningKey active = resolver.findActive().orElse(null);

        if (active == null) {
            requireActiveForExport();
            return;
        }

        if (!isExpired(active)) {
            return;
        }

        acquireAdvisoryLock();

        AuditSigningKey afterLock = repository.findActiveForUpdate().orElse(null);

        if (afterLock != null && !isExpired(afterLock)) {
            return;
        }

        rotate("EXPIRED", afterLock == null ? null : afterLock.getKeyId());
    }

    private boolean isExpired(AuditSigningKey key) {
        if (key.getExpiresAt() == null) {
            return true;
        }

        return Instant.now().isAfter(key.getExpiresAt());
    }

    private void acquireAdvisoryLock() {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(?)",
                Object.class,
                ROTATION_LOCK_KEY
        );
    }

    @Transactional
    private AuditSigningKey rotate(String reason, String oldKeyId) {
        final int maxAttempts = 5;

        repository.deactivateAll();
        repository.flush();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String nextKeyId = generateNextVersionKeyId();

            KeyPair kp = generateRsaKeyPair();

            String publicPem = RsaKeyCodec.toPem(kp.getPublic());
            String fingerprint = RsaKeyCodec.fingerprintSha256Hex(kp.getPublic());

            if (repository.existsByFingerprintSha256Hex(fingerprint)) {
                continue;
            }

            byte[] privatePkcs8Der = kp.getPrivate().getEncoded();
            byte[] encrypted = crypto.encrypt(privatePkcs8Der);

            AuditSigningKey newKey = AuditSigningKey.create(
                    nextKeyId,
                    publicPem,
                    encrypted,
                    fingerprint,
                    maxAgeDays
            );

            try {
                repository.saveAndFlush(newKey);

                String correlationId = MDC.get("correlationId");
                if (correlationId == null) {
                    correlationId = "rotation-" + nextKeyId;
                }

                log.info("security_event {}",
                        kv("schema_version", "docflow_siem_v1"),
                        kv("event.category", "key_management"),
                        kv("event.action", "audit_export_key_rotation"),
                        kv("event.outcome", "success"),
                        kv("keyrotation.reason", reason),
                        kv("keyrotation.old_key_id", oldKeyId),
                        kv("keyrotation.new_key_id", nextKeyId),
                        kv("keyrotation.fingerprint", fingerprint),
                        kv("correlation.id", correlationId)
                );

                return newKey;
            } catch (DataIntegrityViolationException ex) {
                if (attempt == maxAttempts) {
                    log.error("security_event {}",
                            kv("schema_version", "docflow_siem_v1"),
                            kv("event.category", "key_management"),
                            kv("event.action", "audit_export_key_rotation"),
                            kv("event.outcome", "failure"),
                            kv("keyrotation.reason", reason),
                            kv("keyrotation.old_key_id", oldKeyId),
                            kv("error", ex.getClass().getSimpleName()),
                            ex
                    );
                    throw ex;
                }
            }
        }

        throw new IllegalStateException("Failed to rotate signing key");
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(rsaBits);

            KeyPair kp = kpg.generateKeyPair();

            if (!(kp.getPublic() instanceof RSAPublicKey)) {
                throw new IllegalStateException("Generated non RSA key");
            }

            return kp;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA keypair", e);
        }
    }

    private String generateNextVersionKeyId() {
        long max = repository.findMaxKeyVersion(KEY_PREFIX);
        return KEY_PREFIX + (max + 1);
    }
}