package com.brutecx.docflow_backend.api.dto.invite;

import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;

import java.time.Instant;
import java.util.UUID;

public record InviteAdminViewDTO(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String jobTitle,
        String department,
        TenantRole tenantRole,
        InviteStatus status,
        Instant createdAt,
        Instant expiresAt,
        long ageSeconds,
        UUID tenantId,
        String tenantName
) {
}