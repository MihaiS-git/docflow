package com.brutecx.docflow_backend.api.dto.user;

import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserStatus;

import java.util.UUID;

public record LocalUserDTO(
        UUID id,
        UserStatus status
) {

    public static LocalUserDTO from(User user) {
        return new LocalUserDTO(
                user.getId(),
                user.getStatus()
        );
    }
}