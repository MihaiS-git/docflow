package com.brutecx.docflow_backend.api.error;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String errorCode;
    private final String message;
    private final String path;

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
                .build();
    }
}
