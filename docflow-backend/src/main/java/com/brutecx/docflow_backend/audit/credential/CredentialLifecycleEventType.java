package com.brutecx.docflow_backend.audit.credential;

public enum CredentialLifecycleEventType {

    PASSWORD_CHANGED,
    PASSWORD_RESET,

    MFA_ENROLLED,
    MFA_REMOVED,

    REQUIRED_ACTION_SET,
    REQUIRED_ACTION_CLEARED,

    UNKNOWN
}
