package com.brutecx.docflow_backend.domain.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

public final class RsaKeyCodec {

    private static final String KEY_ALG = "RSA";
    private static final HexFormat HEX = HexFormat.of();

    private RsaKeyCodec() {}

    public static PublicKey decodePublicKeyPem(String pem) {
        try {
            if (pem == null) throw new IllegalArgumentException("publicKeyPem is required");
            String normalized = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            if (normalized.isBlank()) {
                throw new IllegalArgumentException("Empty PUBLIC KEY PEM content");
            }

            byte[] der = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            KeyFactory kf = KeyFactory.getInstance(KEY_ALG);
            return kf.generatePublic(spec);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode PUBLIC KEY PEM", e);
        }
    }

    public static String fingerprintSha256HexFromPem(String pem) {
        PublicKey pk = decodePublicKeyPem(pem);
        return fingerprintSha256Hex(pk);
    }

    public static String fingerprintSha256Hex(PublicKey publicKey) {
        try {
            byte[] der = publicKey.getEncoded();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(md.digest(der));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String toPem(PublicKey publicKey) {
        String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        sb.append("-----END PUBLIC KEY-----\n");
        return sb.toString();
    }

    public static String normalizePem(String pem) {
        if (pem == null) return null;
        return pem.trim().replace("\r\n", "\n").replace("\r", "\n");
    }

    public static String bytesToUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}