package com.brutecx.docflow_backend.api.error;

public class InviteException extends ApiException {

    public InviteException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}