package com.brutecx.docflow_backend.audit.sensitive;

/**
 * Defines the primary subject of a sensitive access audit event.
 *
 * This enum answers the question:
 *   "WHAT is the security-relevant object being accessed or inspected?"
 *
 * The subject is NOT the actor (who performed the action),
 * but the entity or domain object whose data or structure is being read.
 */
public enum SensitiveAccessSubjectType {
    /**
     * Access to an individual user identity or profile.
     *
     * Examples:
     * - Viewing a user's account details
     * - Inspecting authentication status or MFA configuration
     * - Reading identity projection data
     */
    USER,

    /**
     * Access to tenant-level metadata or configuration.
     *
     * Examples:
     * - Viewing tenant security settings
     * - Reading tenant status, lifecycle state, or policy flags
     * - Inspecting tenant-scoped administrative data
     */
    TENANT,

    /**
     * Access to audit data itself.
     *
     * Examples:
     * - Reading authentication audit events
     * - Querying RBAC denial logs
     * - Exporting audit records for compliance or incident review
     *
     * This is especially sensitive and typically REGULATED data.
     */
    AUDIT_STREAM,

    /**
     * Access to authorization or privilege topology.
     *
     * Examples:
     * - Listing roles and permissions
     * - Inspecting role-to-permission mappings
     * - Reviewing "who has access to what"
     *
     * Often used during security reviews or investigations.
     */
    RBAC_TOPOLOGY,

    /**
     * Access to security-critical configuration.
     *
     * Examples:
     * - Authentication policies
     * - MFA enforcement rules
     * - Session or lifecycle constraints
     * - Security feature flags
     */
    SECURITY_CONFIGURATION,

    /**
     * Access to internal system-level or cross-cutting security data.
     *
     * Examples:
     * - Internal diagnostics
     * - System health or security metadata
     * - Non-tenant-scoped security internals
     *
     * Use sparingly and only when no other subject type applies.
     */
    SYSTEM
}
