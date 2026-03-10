package com.brutecx.docflow_backend.api.error;

import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String errorCode;
    private final String message;
    private final String path;
    private final Map<String, Object> details;

    private ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String errorCode,
            String message,
            String path,
            Map<String, Object> details
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
        this.details = details;
    }

    public static ErrorResponse fromTemplate(
            ErrorTemplate template,
            String message,
            String path
    ) {
        return new ErrorResponse(
                Instant.now(),
                template.getStatus(),
                template.getError(),
                template.getErrorCode().name(),
                message,
                path,
                null
        );
    }

    public static ErrorResponse create(
            int status,
            String error,
            ErrorCode errorCode,
            String message,
            String path
    ) {
        return new ErrorResponse(
                Instant.now(),
                status,
                error,
                errorCode.name(),
                message,
                path,
                null
        );
    }

    public static ErrorResponse create(
            int status,
            String error,
            ErrorCode errorCode,
            String message,
            String path,
            Map<String, Object> details
    ) {
        return new ErrorResponse(
                Instant.now(),
                status,
                error,
                errorCode.name(),
                message,
                path,
                details
        );
    }
}