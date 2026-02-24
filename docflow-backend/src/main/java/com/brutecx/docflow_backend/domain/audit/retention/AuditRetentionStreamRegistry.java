package com.brutecx.docflow_backend.domain.audit.retention;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central mapping for retention enforcement.
 * IMPORTANT:
 * - This is strictly additive, does NOT touch chain logic/canonical/export code.
 * - Assumes each audit table has:
 *   - id UUID PK column: "id"
 *   - timestamp column: "timestamp"
 *   - correlation_id column: "correlation_id" (may be nullable)
 */
public final class AuditRetentionStreamRegistry {

    private AuditRetentionStreamRegistry() {}

    public record StreamTable(
            String streamName,
            String tableName,
            String timestampColumn,
            String idColumn,
            String correlationIdColumn
    ) {}

    public static final Map<String, StreamTable> STREAMS = build();

    public static Set<String> streamNames() {
        return STREAMS.keySet();
    }

    private static Map<String, StreamTable> build() {
        Map<String, StreamTable> m = new LinkedHashMap<>();

        m.put("ADMIN_ACTIONS", new StreamTable(
                "ADMIN_ACTIONS",
                "admin_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("AUTHENTICATION", new StreamTable(
                "AUTHENTICATION",
                "authentication_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("CREDENTIAL_LIFECYCLE", new StreamTable(
                "CREDENTIAL_LIFECYCLE",
                "credential_lifecycle_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("IDENTITY_PROJECTION", new StreamTable(
                "IDENTITY_PROJECTION",
                "identity_projection_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("LIFECYCLE_DENIED", new StreamTable(
                "LIFECYCLE_DENIED",
                "lifecycle_denied_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("ONBOARDING", new StreamTable(
                "ONBOARDING",
                "onboarding_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("RBAC_DENIED", new StreamTable(
                "RBAC_DENIED",
                "rbac_denied_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("SENSITIVE_ACCESS", new StreamTable(
                "SENSITIVE_ACCESS",
                "sensitive_access_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("UNAUTHENTICATED_ACCESS", new StreamTable(
                "UNAUTHENTICATED_ACCESS",
                "unauthenticated_access_audit_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        m.put("AUDIT_EXPORT_SIGNING_KEY_ROTATION", new StreamTable(
                "AUDIT_EXPORT_SIGNING_KEY_ROTATION",
                "audit_export_signing_key_rotation_events",
                "timestamp",
                "id",
                "correlation_id"
        ));

        return Map.copyOf(m);
    }
}