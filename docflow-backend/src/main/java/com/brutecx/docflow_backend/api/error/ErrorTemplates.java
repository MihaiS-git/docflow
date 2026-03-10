package com.brutecx.docflow_backend.api.error;

import org.springframework.http.HttpStatus;

public final class ErrorTemplates {

    private ErrorTemplates() {}

    public static final ErrorTemplate FORBIDDEN =
            new ErrorTemplate(
                    HttpStatus.FORBIDDEN.value(),
                    HttpStatus.FORBIDDEN.getReasonPhrase(),
                    ErrorCode.FORBIDDEN
            );

    public static final ErrorTemplate UNAUTHORIZED =
            new ErrorTemplate(
                    HttpStatus.UNAUTHORIZED.value(),
                    HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                    ErrorCode.UNAUTHORIZED
            );

    public static final ErrorTemplate ACCOUNT_LOCKED =
            new ErrorTemplate(
                    HttpStatus.FORBIDDEN.value(),
                    HttpStatus.FORBIDDEN.getReasonPhrase(),
                    ErrorCode.ACCOUNT_LOCKED
            );
}