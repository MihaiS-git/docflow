package com.brutecx.docflow_backend.audit.provenance;

/**
 * Where the audited action executed.
 */
public enum ExecutionContext {
    HTTP,
    SCHEDULED_JOB,
    AUTH_FLOW,
    ADMIN_API,
    SYSTEM
}
