package com.brutecx.docflow_backend.api.error;

public class BootstrapActivationNotAllowedException extends ApiException {

    public BootstrapActivationNotAllowedException(String message) {
        super(ErrorCode.BOOTSTRAP_ACTIVATION_NOT_ALLOWED, message);
    }
}