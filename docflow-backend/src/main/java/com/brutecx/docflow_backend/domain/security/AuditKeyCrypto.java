package com.brutecx.docflow_backend.domain.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AuditKeyCrypto {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final byte V1 = 0x01;

    private final byte[] masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuditKeyCrypto(
            @Value("${docflow.audit.export.signing.master-key:}") String inlineKey,
            @Value("${DOCFLOW_AUDIT_KEY_ENCRYPTION_MASTER_FILE:}") String filePath
    ) {

        byte[] decoded;

        try {

            // 1. Test override (preferred in test profile)
            if (inlineKey != null && !inlineKey.isBlank()) {
                decoded = Base64.getDecoder().decode(inlineKey.trim());
            }

            // 2. Production docker secret fallback
            else if (filePath != null && !filePath.isBlank()) {
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
            throw new IllegalStateException("Failed to load audit master key", e);
        }

        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "Audit master key must decode to exactly 32 bytes (got " +
                            decoded.length + ")"
            );
        }

        this.masterKey = decoded;
    }

    public byte[] encrypt(byte[] plaintext) {
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

            return out;

        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt private key", e);
        }
    }

    public byte[] decrypt(byte[] blob) {
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

            return c.doFinal(ct);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt private key", e);
        }
    }
}