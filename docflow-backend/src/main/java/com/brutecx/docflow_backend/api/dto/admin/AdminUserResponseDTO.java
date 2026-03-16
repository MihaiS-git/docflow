package com.brutecx.docflow_backend.api.dto.admin;

import com.brutecx.docflow_backend.domain.user.UserStatus;

import java.util.List;
import java.util.UUID;

public record AdminUserResponseDTO(
        UUID id,
        String email,
        UserStatus status,
        List<String> roles,
        boolean identityProvisioned
) {
}
