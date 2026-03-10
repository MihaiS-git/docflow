package com.brutecx.docflow_backend.api.error;

public class SelfActionForbiddenException extends ApiException {

    public SelfActionForbiddenException(String message) {
        super(ErrorCode.SELF_ACTION_FORBIDDEN, message);
    }
}