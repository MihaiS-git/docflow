package com.brutecx.docflow_backend.api.error;

import java.util.UUID;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(UUID userId) {
        super(
                ErrorCode.USER_NOT_FOUND,
                "User not found with ID: " + userId
        );
    }
}