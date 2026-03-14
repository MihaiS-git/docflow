package com.brutecx.docflow_backend.audit.tamper;

import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionStreamRegistry;

import java.util.Locale;
import java.util.Objects;

public record AuditPartition(
        String stream,
        String type,
        String value
) {

    public AuditPartition {
        stream = normalizeStream(stream);
        type = normalizeType(type);
        value = normalizeValue(value);
    }

    public String toStateKey() {
        return stream + "|" + type + ":" + value;
    }

    public String partitionValue() {
        return type + ":" + value;
    }

    public static AuditPartition global(String stream) {
        return new AuditPartition(stream, "GLOBAL", "GLOBAL");
    }

    public static AuditPartition tenant(String stream, String tenantId) {
        return new AuditPartition(stream, "TENANT", tenantId);
    }

    public static AuditPartition subject(String stream, String subjectId) {
        return new AuditPartition(stream, "SUBJECT", subjectId);
    }

    private static String normalizeStream(String v) {
        Objects.requireNonNull(v, "Stream cannot be null");

        String s = v.trim().toUpperCase(Locale.ROOT);

        if (!AuditRetentionStreamRegistry.STREAMS.containsKey(s)) {
            throw new IllegalArgumentException("Unknown audit stream: " + s);
        }

        return s;
    }

    private static String normalizeType(String v) {
        Objects.requireNonNull(v, "Partition type cannot be null");

        String s = v.trim().toUpperCase(Locale.ROOT);

        return switch (s) {
            case "GLOBAL", "TENANT", "SUBJECT" -> s;
            default -> throw new IllegalArgumentException("Invalid partition type: " + v);
        };
    }

    private static String normalizeValue(String v) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Partition value cannot be null/blank");
        }

        return v.trim();
    }
}