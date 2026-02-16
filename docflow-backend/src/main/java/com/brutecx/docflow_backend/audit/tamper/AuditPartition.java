package com.brutecx.docflow_backend.audit.tamper;

public record AuditPartition(
        String stream,
        String type,
        String value
) {

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
        return new AuditPartition(stream, "TENANT", normalize(tenantId));
    }

    public static AuditPartition subject(String stream, String subjectId) {
        return new AuditPartition(stream, "SUBJECT", normalize(subjectId));
    }

    private static String normalize(String v) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Partition value cannot be null/blank");
        }
        return v.trim();
    }
}
