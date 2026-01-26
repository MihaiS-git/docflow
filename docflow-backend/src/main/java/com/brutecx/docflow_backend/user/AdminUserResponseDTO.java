package com.brutecx.docflow_backend.user;

import java.util.UUID;

public record AdminUserResponseDTO(
        UUID id,
        String email,
        UserStatus status
) {}
