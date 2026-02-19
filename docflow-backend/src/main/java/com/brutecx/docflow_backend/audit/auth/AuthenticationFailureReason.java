package com.brutecx.docflow_backend.audit.auth;

public enum AuthenticationFailureReason {
    INVALID_CREDENTIALS,
    USER_NOT_FOUND,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,
    PASSWORD_EXPIRED,
    MFA_FAILED,
    UNKNOWN
}
