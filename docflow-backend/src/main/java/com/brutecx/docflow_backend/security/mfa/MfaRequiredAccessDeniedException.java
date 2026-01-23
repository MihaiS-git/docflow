package com.brutecx.docflow_backend.security.mfa;

import org.springframework.security.access.AccessDeniedException;

public class MfaRequiredAccessDeniedException extends AccessDeniedException {

    private final String errorCode;

    public MfaRequiredAccessDeniedException(String message) {
        super(message);
        this.errorCode = "mfa_required";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
