package com.brutecx.docflow_backend.api.error;

import lombok.Getter;

@Getter
public final class ErrorTemplate {

    private final int status;
    private final String error;
    private final ErrorCode errorCode;

    public ErrorTemplate(int status, String error, ErrorCode errorCode) {
        this.status = status;
        this.error = error;
        this.errorCode = errorCode;
    }
}