package com.brutecx.docflow_backend.api.dto.invite;


import com.brutecx.docflow_backend.domain.invite.InviteStatus;

import java.time.Instant;
import java.util.UUID;

public record InviteAdminListItemDTO(
        UUID id,
        String email,
        InviteStatus status,
        Instant createdAt,
        Instant expiresAt,
        long ageSeconds
) {
}