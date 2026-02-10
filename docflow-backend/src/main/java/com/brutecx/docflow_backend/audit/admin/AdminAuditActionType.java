package com.brutecx.docflow_backend.audit.admin;

public enum AdminAuditActionType {
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    USER_ACTIVATED,
    USER_LOCKED,
    USER_DISABLED,

    USER_INVITED,
    INVITE_CLEANUP,
    INVITE_REVOKED,

    TENANT_CREATED,
    TENANT_UPDATED,
    TENANT_SUSPENDED,
    TENANT_MUTATION_DENIED
}
