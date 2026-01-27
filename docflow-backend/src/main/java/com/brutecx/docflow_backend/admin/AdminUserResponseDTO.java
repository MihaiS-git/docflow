package com.brutecx.docflow_backend.admin;

import com.brutecx.docflow_backend.user.UserStatus;

import java.util.List;
import java.util.UUID;

public record AdminUserResponseDTO(
        UUID id,
        String email,
        UserStatus status,
        List<String> roles
) {}
