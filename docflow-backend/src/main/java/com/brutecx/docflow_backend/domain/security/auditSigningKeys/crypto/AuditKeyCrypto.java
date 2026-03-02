package com.brutecx.docflow_backend.domain.security.auditSigningKeys.crypto;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import static net.logstash.logback.argument.StructuredArguments.kv;


@Service
public class AuditKeyCrypto {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final byte V1 = 0x01;

    private final byte[] masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    private final Counter encryptSuccess;
    private final Counter encryptFailure;
    private final Counter decryptSuccess;
    private final Counter decryptFailure;
    private final Timer encryptTimer;
    private final Timer decryptTimer;

    public AuditKeyCrypto(
            @Value("${docflow.audit.export.signing.master-key:}") String inlineKey,
            @Value("${DOCFLOW_AUDIT_KEY_ENCRYPTION_MASTER_FILE:}") String filePath,
            MeterRegistry registry
    ) {
        byte[] decoded;
        String source = "NONE";

        try {
            // 1. Test override (preferred in test profile)
            if (inlineKey != null && !inlineKey.isBlank()) {
                source = "INLINE";
                decoded = Base64.getDecoder().decode(inlineKey.trim());
            }

            // 2. Production docker secret fallback
            else if (filePath != null && !filePath.isBlank()) {
                source = "FILE";
                String base64 = Files.readString(
                        Path.of(filePath.trim()),
                        StandardCharsets.UTF_8
                );
                decoded = Base64.getDecoder().decode(base64.trim());
            } else {
                throw new IllegalStateException(
                        "No audit signing master key configured. " +
                                "Provide docflow.audit.export.signing.master-key (tests) " +
                                "or DOCFLOW_AUDIT_KEY_ENCRYPTION_MASTER_FILE (production)."
                );
            }
        } catch (Exception e) {
            log.error("audit_key_master_load_failed",
                    kv("source", source),
                    kv("error", e.getClass().getSimpleName()),
                    e
            );
            throw new IllegalStateException("Failed to load audit master key", e);
        }

        if (decoded.length != 32) {
            log.error("audit_key_master_invalid_length",
                    kv("source", source),
                    kv("bytes", decoded.length)
            );
            throw new IllegalStateException(
                    "Audit master key must decode to exactly 32 bytes (got " +
                            decoded.length + ")"
            );
        }

        this.masterKey = decoded;

        this.encryptSuccess = Counter.builder("docflow_audit_key_crypto_ops_total")
                .tag("op", "encrypt").tag("result", "success")
                .register(registry);
        this.encryptFailure = Counter.builder("docflow_audit_key_crypto_ops_total")
                .tag("op", "encrypt").tag("result", "failure")
                .register(registry);
        this.decryptSuccess = Counter.builder("docflow_audit_key_crypto_ops_total")
                .tag("op", "decrypt").tag("result", "success")
                .register(registry);
        this.decryptFailure = Counter.builder("docflow_audit_key_crypto_ops_total")
                .tag("op", "decrypt").tag("result", "failure")
                .register(registry);

        this.encryptTimer = Timer.builder("docflow_audit_key_crypto_seconds")
                .tag("op", "encrypt")
                .publishPercentileHistogram()
                .register(registry);
        this.decryptTimer = Timer.builder("docflow_audit_key_crypto_seconds")
                .tag("op", "decrypt")
                .publishPercentileHistogram()
                .register(registry);

        log.info("audit_key_master_loaded",
                kv("source", source),
                kv("bytes", decoded.length)
        );
    }

    public byte[] encrypt(byte[] plaintext) {
        final long startNs = System.nanoTime();
        boolean success = false;
        try {
            if (plaintext == null || plaintext.length == 0) {
                throw new IllegalArgumentException("plaintext required");
            }

            byte[] iv = new byte[IV_LEN];
            secureRandom.nextBytes(iv);

            Cipher c = Cipher.getInstance(CIPHER);
            c.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            byte[] cipherText = c.doFinal(plaintext);

            byte[] out = new byte[1 + IV_LEN + cipherText.length];
            out[0] = V1;
            System.arraycopy(iv, 0, out, 1, IV_LEN);
            System.arraycopy(cipherText, 0, out, 1 + IV_LEN, cipherText.length);

            encryptSuccess.increment();
            success = true;
            return out;
        } catch (Exception e) {
            encryptFailure.increment();
            log.error("audit_key_encrypt_failed",
                    kv("error", e.getClass().getSimpleName()),
                    e
            );
            throw new IllegalStateException("Failed to encrypt private key", e);
        } finally {
            if (success) {
                encryptTimer.record(Duration.ofNanos(System.nanoTime() - startNs));
            }
        }
    }

    public byte[] decrypt(byte[] blob) {
        final long startNs = System.nanoTime();
        boolean success = false;
        try {
            if (blob == null || blob.length < 1 + IV_LEN + 1) {
                throw new IllegalArgumentException("Invalid encrypted blob");
            }

            if (blob[0] != V1) {
                throw new IllegalArgumentException(
                        "Unsupported encrypted blob version: " + blob[0]
                );
            }

            byte[] iv = new byte[IV_LEN];
            System.arraycopy(blob, 1, iv, 0, IV_LEN);

            int ctLen = blob.length - 1 - IV_LEN;
            byte[] ct = new byte[ctLen];
            System.arraycopy(blob, 1 + IV_LEN, ct, 0, ctLen);

            Cipher c = Cipher.getInstance(CIPHER);
            c.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            byte[] out = c.doFinal(ct);
            decryptSuccess.increment();
            success = true;
            return out;
        } catch (Exception e) {
            decryptFailure.increment();
            log.error("audit_key_decrypt_failed",
                    kv("error", e.getClass().getSimpleName()),
                    kv("blob_len", blob == null ? 0 : blob.length),
                    e
            );
            throw new IllegalStateException("Failed to decrypt private key", e);
        } finally {
            if (success) {
                decryptTimer.record(Duration.ofNanos(System.nanoTime() - startNs));
            }
        }
    }
}