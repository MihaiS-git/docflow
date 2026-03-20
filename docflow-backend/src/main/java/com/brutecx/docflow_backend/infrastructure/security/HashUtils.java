package com.brutecx.docflow_backend.infrastructure.security;

public final class HashUtils {

    private HashUtils() {
    }

    public static String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cannot hash null/blank value");
        }
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}