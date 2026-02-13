package com.brutecx.docflow_backend.api.error;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

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
            ErrorCode errorCode,
            String message,
            String path
    ) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .errorCode(errorCode.name())
                .message(message)
                .path(path)
                .details(null)
                .build();
    }

    public static ErrorResponse of(
            int status,
            String error,
            ErrorCode errorCode,
            String message,
            String path,
            Map<String, Object> details
    ) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .errorCode(errorCode.name())
                .message(message)
                .path(path)
                .details(details)
                .build();
    }
}
