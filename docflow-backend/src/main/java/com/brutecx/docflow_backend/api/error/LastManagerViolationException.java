package com.brutecx.docflow_backend.api.error;

public class LastManagerViolationException extends ApiException {

    public LastManagerViolationException(String message) {
        super(ErrorCode.LAST_MANAGER_VIOLATION, message);
    }
}