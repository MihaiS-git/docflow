package com.brutecx.docflow_backend.api.error;

public class BootstrapActivationDeniedException extends RuntimeException {
    public BootstrapActivationDeniedException(String message) {
        super(message);
    }
}