package com.brutecx.docflow_backend.audit.canonical;

/**
 * Stream-specific canonical material builder.
 * Writers and verifiers MUST share the same implementation to prevent drift.
 */
public interface AuditCanonicalMaterialBuilder<T> {
    String stream();

    String buildCanonicalMaterial(T input);
}
