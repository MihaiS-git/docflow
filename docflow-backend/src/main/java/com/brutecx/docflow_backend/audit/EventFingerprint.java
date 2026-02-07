package com.brutecx.docflow_backend.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for generating deterministic, idempotent event fingerprints.
 * <p>
 * Fingerprints are used to:
 * - ensure audit event idempotency
 * - detect duplicate or replayed events
 * - support forensic integrity guarantees (ISO 27001 / NIS2)
 * <p>
 * Rules:
 * - input parts MUST be stable and deterministic
 * - timestamps, random values, or UUIDs MUST NOT be included
 * - null values are normalized to "-"
 * - values are trimmed and upper-cased for canonicalization
 * - fingerprint format is versioned
 */
public final class EventFingerprint {

    /**
     * Version prefix to allow future evolution without breaking old data
     */
    private static final String VERSION = "v1";

    /**
     * Delimiter chosen outside common identifier character sets
     */
    private static final char DELIMITER = '|';

    /**
     * Placeholder for null or missing values
     */
    private static final String NULL_PLACEHOLDER = "-";

    public static String of(List<String> parts) {
        Objects.requireNonNull(parts, "parts must not be null");

        if (parts.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate fingerprint from empty parts list");
        }

        String raw = VERSION + DELIMITER +
                parts.stream()
                        .map(EventFingerprint::normalize)
                        .reduce((a, b) -> a + DELIMITER + b)
                        .orElseThrow();

        return sha256(raw);
    }

    private static String normalize(String value) {
        if (value == null) {
            return NULL_PLACEHOLDER;
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            return NULL_PLACEHOLDER;
        }

        // Canonicalize to avoid casing / whitespace inconsistencies
        return normalized.toUpperCase();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash audit event fingerprint", e);
        }
    }

    private EventFingerprint() {
    }
}
