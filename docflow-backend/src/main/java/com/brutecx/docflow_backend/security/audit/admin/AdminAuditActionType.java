package com.brutecx.docflow_backend.security.audit.admin;

public enum AdminAuditActionType {
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    USER_ACTIVATED,
    USER_LOCKED,
    USER_DISABLED
}
