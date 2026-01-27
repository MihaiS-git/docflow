package com.brutecx.docflow_backend.api.error;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID userId) {

        super("User not found with ID: " + userId);
    }
}
