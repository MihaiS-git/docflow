package com.brutecx.docflow_backend.api.error;

public class UserAlreadyTenantMemberException extends ApiException {

    public UserAlreadyTenantMemberException(String message) {
        super(ErrorCode.USER_ALREADY_TENANT_MEMBER, message);
    }
}