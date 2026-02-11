package com.brutecx.docflow_backend.api.dto.invite;


import com.brutecx.docflow_backend.domain.invite.InviteStatus;

import java.time.Instant;
import java.util.UUID;

public record InviteAdminViewDTO(
        UUID id,
        String email,
        InviteStatus status,
        Instant createdAt,
        Instant expiresAt,
        long ageSeconds,
        UUID tenantId,
        String tenantName
) {
}