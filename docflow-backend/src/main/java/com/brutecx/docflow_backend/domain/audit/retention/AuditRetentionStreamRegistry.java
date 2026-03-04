package com.brutecx.docflow_backend.domain.audit.retention;

import java.util.*;

/**
 * Central mapping for retention enforcement.
 * IMPORTANT:
 * - This is strictly additive, does NOT touch chain logic/canonical/export code.
 * - Assumes each audit table has:
 * - id UUID PK column: "id"
 * - timestamp column: "timestamp"
 * - correlation_id column: "correlation_id" (may be nullable)
 */
public final class AuditRetentionStreamRegistry {

    private AuditRetentionStreamRegistry() {
    }

    public record StreamTable(
            String streamName,
            String tableName,
            String timestampColumn,
            String idColumn,
            String correlationIdColumn,
            int defaultRetentionDays,
            boolean defaultArchiveEnabled
    ) {
        public StreamTable {
            if (streamName == null || streamName.isBlank()) {
                throw new IllegalArgumentException("streamName must not be blank");
            }
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("tableName must not be blank");
            }
            if (timestampColumn == null || timestampColumn.isBlank()) {
                throw new IllegalArgumentException("timestampColumn must not be blank");
            }
            if (idColumn == null || idColumn.isBlank()) {
                throw new IllegalArgumentException("idColumn must not be blank");
            }
            if (defaultRetentionDays <= 0) {
                throw new IllegalArgumentException("defaultRetentionDays must be > 0");
            }
        }
    }

    public static final Map<String, StreamTable> STREAMS = build();

    /**
     * System defaults per stream.
     * DB rows (AuditRetentionPolicy) are overrides only.
     */
    public record RetentionDefault(int retentionDays, boolean archiveEnabled) {
        public RetentionDefault {
            if (retentionDays <= 0) {
                throw new IllegalArgumentException("retentionDays must be > 0");
            }
        }
    }

    public static final Set<String> STREAM_NAMES = Set.copyOf(STREAMS.keySet());

    public static RetentionDefault defaultFor(String streamName) {
        if (streamName == null || streamName.isBlank()) {
            throw new IllegalArgumentException("streamName must not be blank");
        }

        String normalized = streamName.trim().toUpperCase(Locale.ROOT);

        StreamTable table = STREAMS.get(normalized);

        if (table == null) {
            throw new IllegalArgumentException("Unknown audit stream: " + streamName);
        }

        return new RetentionDefault(
                table.defaultRetentionDays(),
                table.defaultArchiveEnabled()
        );
    }

    private static Map<String, StreamTable> build() {
        Map<String, StreamTable> m = new LinkedHashMap<>();

        m.put("ADMIN_ACTIONS", new StreamTable(
                "ADMIN_ACTIONS",
                "admin_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                365,
                true
        ));

        m.put("AUTHENTICATION", new StreamTable(
                "AUTHENTICATION",
                "authentication_events",
                "timestamp",
                "id",
                "correlation_id",
                90,
                true
        ));

        m.put("CREDENTIAL_LIFECYCLE", new StreamTable(
                "CREDENTIAL_LIFECYCLE",
                "credential_lifecycle_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                365,
                true
        ));

        m.put("IDENTITY_PROJECTION", new StreamTable(
                "IDENTITY_PROJECTION",
                "identity_projection_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                365,
                true
        ));

        m.put("LIFECYCLE_DENIED", new StreamTable(
                "LIFECYCLE_DENIED",
                "lifecycle_denied_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                365,
                true
        ));

        m.put("ONBOARDING", new StreamTable(
                "ONBOARDING",
                "onboarding_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                365,
                true
        ));

        m.put("RBAC_DENIED", new StreamTable(
                "RBAC_DENIED",
                "rbac_denied_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                90,
                true
        ));

        m.put("SENSITIVE_ACCESS", new StreamTable(
                "SENSITIVE_ACCESS",
                "sensitive_access_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                365,
                true
        ));

        m.put("UNAUTHENTICATED_ACCESS", new StreamTable(
                "UNAUTHENTICATED_ACCESS",
                "unauthenticated_access_audit_events",
                "timestamp",
                "id",
                "correlation_id",
                365,
                true
        ));

        return Map.copyOf(m);
    }
}