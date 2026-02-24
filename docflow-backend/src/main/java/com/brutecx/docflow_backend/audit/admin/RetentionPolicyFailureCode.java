package com.brutecx.docflow_backend.audit.admin;

/**
 * Stable failure classification for retention policy governance.
 * No raw exception messages are stored in audit trail.
 */
public enum RetentionPolicyFailureCode {

    INVALID_STREAM,

    INVALID_RETENTION_DAYS,

    PERSISTENCE_ERROR,

    UNKNOWN_ERROR
}