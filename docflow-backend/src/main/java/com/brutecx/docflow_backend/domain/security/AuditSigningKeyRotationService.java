package com.brutecx.docflow_backend.domain.security;

import com.brutecx.docflow_backend.audit.keyrotation.AuditExportSigningKeyRotationAuditService;
import com.brutecx.docflow_backend.audit.keyrotation.AuditExportSigningKeyRotationReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AuditSigningKeyRotationService {

    private static final int ROTATION_MONTHS = 6;
    private static final String NAMESPACE = "audit-export";
    private static final Pattern KEY_PATTERN = Pattern.compile("^audit-export:v([1-9][0-9]*)$");

    private final AuditSigningKeyRepository repository;
    private final AuditSigningKeyResolver resolver;
    private final AuditSigningKeyRotationLockRepository lockRepository;
    private final AuditKeyCrypto crypto;
    private final AuditExportSigningKeyRotationAuditService rotationAuditService;

    private final Counter rotatedMissingCounter;
    private final Counter rotatedExpiredCounter;

    private final int rsaBits;

    public AuditSigningKeyRotationService(
            AuditSigningKeyRepository repository,
            AuditSigningKeyResolver resolver,
            AuditSigningKeyRotationLockRepository lockRepository,
            AuditKeyCrypto crypto,
            AuditExportSigningKeyRotationAuditService rotationAuditService,
            MeterRegistry meterRegistry,
            @Value("${docflow.audit.export.signing.rsa-bits:3072}") int rsaBits
    ) {
        this.repository = repository;
        this.resolver = resolver;
        this.lockRepository = lockRepository;
        this.crypto = crypto;
        this.rotationAuditService = rotationAuditService;
        this.rsaBits = (rsaBits == 2048 || rsaBits == 3072) ? rsaBits : 3072;

        this.rotatedMissingCounter = Counter.builder("docflow.audit.export.signing_key.rotated_missing")
                .register(meterRegistry);

        this.rotatedExpiredCounter = Counter.builder("docflow.audit.export.signing_key.rotated_expired")
                .register(meterRegistry);
    }

    /**
     * Guarantees a non-expired active key.
     * Auto-rotates inside same transaction if needed.
     */
    @Transactional
    public AuditSigningKey requireActiveForExport() {

        // First optimistic check (no lock)
        AuditSigningKey current = resolver.findActive().orElse(null);

        if (current != null && !isExpired(current)) {
            return current;
        }

        // Acquire rotation lock
        lockRepository.lockRow();

        // Re-check AFTER acquiring lock
        AuditSigningKey afterLock = repository.findActiveForUpdate().orElse(null);

        if (afterLock != null && !isExpired(afterLock)) {
            return afterLock;
        }

        AuditExportSigningKeyRotationReason reason =
                (afterLock == null)
                        ? AuditExportSigningKeyRotationReason.MISSING
                        : AuditExportSigningKeyRotationReason.EXPIRED;

        String oldKeyId = (afterLock == null) ? null : afterLock.getKeyId();

        return rotate(reason, oldKeyId);
    }

    private boolean isExpired(AuditSigningKey key) {
        return key.getExpiresAt() == null || Instant.now().isAfter(key.getExpiresAt());
    }

    @Transactional
    private AuditSigningKey rotate(
            AuditExportSigningKeyRotationReason reason,
            String oldKeyId
    ) {
        String nextKeyId = generateNextVersionKeyId();

        if (reason == AuditExportSigningKeyRotationReason.MISSING) rotatedMissingCounter.increment();
        if (reason == AuditExportSigningKeyRotationReason.EXPIRED) rotatedExpiredCounter.increment();

        repository.deactivateAll();

        KeyPair kp = generateRsaKeyPair();

        String publicPem = RsaKeyCodec.toPem(kp.getPublic());
        String fingerprint = RsaKeyCodec.fingerprintSha256Hex(kp.getPublic());

        byte[] privatePkcs8Der = kp.getPrivate().getEncoded();
        byte[] encrypted = crypto.encrypt(privatePkcs8Der);

        Instant now = Instant.now();

        // ✅ FIX: use calendar-based arithmetic safely
        Instant expiresAt = now.atOffset(ZoneOffset.UTC)
                .plusMonths(ROTATION_MONTHS)
                .toInstant();

        AuditSigningKey newKey = new AuditSigningKey(
                nextKeyId,
                publicPem,
                encrypted,
                fingerprint,
                now,
                expiresAt,
                true
        );

        repository.saveAndFlush(newKey);
        resolver.evict(nextKeyId);

        String correlationId = MDC.get("correlationId");

        log.info(
                "AUDIT_EXPORT_SIGNING_KEY_ROTATED reason={} oldKeyId={} newKeyId={} ts={} correlationId={}",
                reason,
                oldKeyId,
                nextKeyId,
                now,
                correlationId
        );

        rotationAuditService.recordRotation(reason, oldKeyId, nextKeyId, fingerprint);

        return newKey;
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(rsaBits);
            KeyPair kp = kpg.generateKeyPair();

            if (!(kp.getPublic() instanceof RSAPublicKey)) {
                throw new IllegalStateException("Generated non-RSA public key");
            }

            return kp;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA keypair", e);
        }
    }

    private String generateNextVersionKeyId() {

        long max = repository.findAll().stream()
                .map(AuditSigningKey::getKeyId)
                .map(KEY_PATTERN::matcher)
                .filter(Matcher::matches)
                .mapToLong(m -> Long.parseLong(m.group(1)))
                .max()
                .orElse(0L);

        long next = max + 1;
        return NAMESPACE + ":v" + next;
    }
}