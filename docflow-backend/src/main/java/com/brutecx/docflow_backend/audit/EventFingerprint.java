package com.brutecx.docflow_backend.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Utility class for generating event fingerprints.
 * An event fingerprint is a SHA-256 hash of concatenated event attributes.
 * Null values are replaced with a hyphen ("-") before hashing.
 * The parts are joined using a pipe ("|") as a delimiter.
 * Example:
 *   parts = ["user123", "LOGIN_SUCCESS", null, "192.168.
 *   1.1"]
 *   raw = "user123|LOGIN_SUCCESS|-|
 */
public final class EventFingerprint {

    public static String of(List<String> parts) {
        String raw = String.join("|", parts.stream()
                .map(v -> v == null ? "-" : v)
                .toList());
        return sha256(raw);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash auth event", e);
        }
    }

    private EventFingerprint() {}
}
