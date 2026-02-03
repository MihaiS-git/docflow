package com.brutecx.docflow_backend.api.error;

public class UserNotFoundLocallyException extends RuntimeException {
    public UserNotFoundLocallyException(String message) {
        super(message);
    }
}
