package com.brutecx.docflow_backend.security.audit;

import lombok.Getter;

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
