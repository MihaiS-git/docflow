package com.brutecx.docflow_backend.audit.auth;

import lombok.Getter;

/**
 * Enumeration representing the result of an authentication event.
 * SUCCESS - Authentication was successful.
 * FAILURE - Authentication failed.
 * LOGOUT  - User has logged out.
 */
@Getter
public enum AuthenticationResult {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE"),
    LOGOUT("LOGOUT");

    private final String value;

    AuthenticationResult(String value) {
        this.value = value;
    }

}
