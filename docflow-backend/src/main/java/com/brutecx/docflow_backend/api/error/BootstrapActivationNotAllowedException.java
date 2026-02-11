package com.brutecx.docflow_backend.api.error;

public class BootstrapActivationNotAllowedException extends RuntimeException {
    public BootstrapActivationNotAllowedException(String message) {
        super(message);
    }
}
