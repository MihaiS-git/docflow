package com.brutecx.docflow_backend.api.error;

public class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException(String message) {
        super(message);
    }
}
