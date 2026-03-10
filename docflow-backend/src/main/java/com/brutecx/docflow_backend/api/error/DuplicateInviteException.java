package com.brutecx.docflow_backend.api.error;

public class DuplicateInviteException extends ApiException {

    public DuplicateInviteException(String message) {
        super(ErrorCode.INVITE_ALREADY_EXISTS, message);
    }
}