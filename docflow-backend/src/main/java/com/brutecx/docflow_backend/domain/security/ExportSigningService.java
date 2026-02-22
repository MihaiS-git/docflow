package com.brutecx.docflow_backend.domain.security;

import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Export signing (AUDITOR-EXPECTED / INDUSTRY STANDARD):
 * - Signature algorithm: SHA256withRSA
 * - Signature input: RAW PAYLOAD BYTES (payload.jsonl bytes), not the SHA-256 digest bytes.
 * Verification should succeed with:
 *   openssl dgst -sha256 -verify pub.pem -signature signature.bin payload.jsonl
 */
@Service
public class ExportSigningService {

    public record SignatureResult(String signatureB64, String algorithm, String keyId) {}

    public record PayloadSigner(
            AuditSigningKey key,
            String algorithm,
            Signature signature
    ) {
        public SignatureResult finish() {
            try {
                byte[] signatureBytes = signature.sign();
                return new SignatureResult(
                        Base64.getEncoder().encodeToString(signatureBytes),
                        algorithm,
                        key.getKeyId()
                );
            } catch (Exception e) {
                throw new IllegalStateException("Failed to finalize payload signature", e);
            }
        }
    }

    private static final String SIGNATURE_ALG = "SHA256withRSA";
    private static final String KEY_ALG = "RSA";

    private final AuditSigningKeyRotationService rotationService;
    private final AuditSigningKeyResolver keyResolver;
    private final AuditKeyCrypto crypto;

    public ExportSigningService(
            AuditSigningKeyRotationService rotationService,
            AuditSigningKeyResolver keyResolver,
            AuditKeyCrypto crypto
    ) {
        this.rotationService = rotationService;
        this.keyResolver = keyResolver;
        this.crypto = crypto;
    }

    /**
     * Begin a streaming signature over the payload bytes.
     * Caller must feed the EXACT bytes written to the payload JSONL (including '\n').
     */
    public PayloadSigner beginPayloadSigner() {
        try {
            AuditSigningKey active = rotationService.requireActiveForExport();

            byte[] pkcs8Der = crypto.decrypt(active.getEncryptedPrivateKey());
            PrivateKey privateKey = loadPkcs8PrivateKey(pkcs8Der);

            Signature sig = Signature.getInstance(SIGNATURE_ALG);
            sig.initSign(privateKey);

            return new PayloadSigner(active, SIGNATURE_ALG, sig);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize payload signer", e);
        }
    }

    /**
     * Verify signature over payload bytes (industry standard), streaming.
     * Reads exactly {@code length} bytes from {@code in}.
     */
    public boolean verifyPayloadSignatureStream(
            InputStream in,
            long length,
            String signatureB64,
            String signatureAlgorithm,
            String keyId
    ) {
        try {
            if (in == null) return false;
            if (length <= 0) return false;
            if (signatureB64 == null || signatureB64.isBlank()) return false;
            if (signatureAlgorithm == null || signatureAlgorithm.isBlank()) return false;
            if (keyId == null || keyId.isBlank()) return false;

            PublicKey pk = keyResolver.requirePublicKey(keyId.trim());
            byte[] signatureBytes = Base64.getDecoder().decode(signatureB64);

            Signature verifier = Signature.getInstance(signatureAlgorithm.trim());
            verifier.initVerify(pk);

            try (InputStream bin = new BufferedInputStream(in)) {
                byte[] buf = new byte[64 * 1024];
                long remaining = length;

                while (remaining > 0) {
                    int r = bin.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (r == -1) break;
                    verifier.update(buf, 0, r);
                    remaining -= r;
                }

                if (remaining != 0) {
                    return false; // file shorter than expected payload length
                }
            }

            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    private static PrivateKey loadPkcs8PrivateKey(byte[] pkcs8Der) {
        try {
            if (pkcs8Der == null || pkcs8Der.length == 0) {
                throw new IllegalArgumentException("Empty PKCS8 DER");
            }
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Der);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALG);
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA PKCS8 private key", e);
        }
    }
}