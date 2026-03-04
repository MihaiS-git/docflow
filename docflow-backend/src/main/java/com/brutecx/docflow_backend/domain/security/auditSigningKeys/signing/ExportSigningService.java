package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

import com.brutecx.docflow_backend.domain.security.auditSigningKeys.crypto.AuditKeyCrypto;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Service
public class ExportSigningService {

    public record SignatureResult(
            String signatureB64,
            String algorithm,
            String keyId
    ) {
        public SignatureResult {
            if (signatureB64 == null || signatureB64.isBlank())
                throw new IllegalArgumentException("signatureB64 required");

            if (algorithm == null || algorithm.isBlank())
                throw new IllegalArgumentException("algorithm required");

            if (keyId == null || keyId.isBlank())
                throw new IllegalArgumentException("keyId required");
        }
    }

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

    private final AuditKeyCrypto crypto;

    public ExportSigningService(
            AuditKeyCrypto crypto
    ) {
        this.crypto = crypto;
    }

    /**
     * Pure signer initialization.
     * Rotation must already have been handled by caller.
     */
    public PayloadSigner beginPayloadSigner(AuditSigningKey activeKey) {
        try {
            if (activeKey == null) {
                throw new IllegalArgumentException("Active signing key required");
            }

            byte[] pkcs8Der = crypto.decrypt(activeKey.getEncryptedPrivateKey());

            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Der);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALG);
            PrivateKey privateKey = kf.generatePrivate(spec);

            Signature sig = Signature.getInstance(SIGNATURE_ALG);
            sig.initSign(privateKey);

            return new PayloadSigner(activeKey, SIGNATURE_ALG, sig);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize payload signer", e);
        }
    }
}