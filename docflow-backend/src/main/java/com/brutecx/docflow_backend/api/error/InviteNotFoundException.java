package com.brutecx.docflow_backend.api.error;

public class InviteNotFoundException extends ApiException {

    public InviteNotFoundException(String message) {
        super(ErrorCode.INVITE_NOT_FOUND, message);
    }
}