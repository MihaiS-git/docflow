package com.brutecx.docflow_backend.api.error;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Data Transfer Object (DTO) for structured error responses in the API.
 * Contains details about the error such as timestamp, status code,
 * error message, error code, and the request path that caused the error.
 * Utilizes Lombok for boilerplate code reduction.
 */
@Getter
@Builder
public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String errorCode;
    private final String message;
    private final String path;
    private final Map<String, Object> details;

    public static ErrorResponse of(
            int status,
            String error,
            String errorCode,
            String message,
            String path
    ) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .details(null)
                .build();
    }

    public static ErrorResponse of(
            int status,
            String error,
            String errorCode,
            String message,
            String path,
            Map<String, Object> details
    ) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .details(details)
                .build();
    }
}
