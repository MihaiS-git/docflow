package com.brutecx.docflow_backend.api.error;

public class BootstrapActivationDeniedException extends ApiException {

    public BootstrapActivationDeniedException(String message) {
        super(ErrorCode.BOOTSTRAP_ACTIVATION_DENIED, message);
    }
}