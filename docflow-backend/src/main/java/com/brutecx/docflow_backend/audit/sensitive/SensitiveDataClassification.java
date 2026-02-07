package com.brutecx.docflow_backend.audit.sensitive;

/**
 * Classifies the sensitivity level of accessed data for compliance and
 * forensic purposes.
 *
 * This enum answers the question:
 *   "HOW sensitive is the data that was accessed?"
 *
 * It supports ISO 27001 data classification and NIS2 incident impact analysis.
 */
public enum SensitiveDataClassification {

    /**
     * Internal, low-risk operational data.
     *
     * Examples:
     * - Non-security operational metadata
     * - High-level status information
     *
     * Still audited, but typically low impact if exposed.
     */
    INTERNAL,

    /**
     * Confidential internal data.
     *
     * Examples:
     * - User profile details
     * - Internal identifiers
     * - Non-public tenant metadata
     *
     * Disclosure could cause limited harm.
     */
    CONFIDENTIAL,

    /**
     * High-risk security-sensitive data.
     *
     * Examples:
     * - Security configuration
     * - Authorization structures
     * - Identity state or enforcement rules
     *
     * Access requires strong justification and is closely monitored.
     */
    RESTRICTED,

    /**
     * Legally or regulatorily protected data.
     *
     * Examples:
     * - Audit logs
     * - Authentication and access history
     * - Data required for incident response or compliance reporting
     *
     * Typically subject to retention, access control, and export rules.
     */
    REGULATED
}
