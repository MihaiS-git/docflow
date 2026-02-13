package com.brutecx.docflow_backend.api.dto.tenant;

import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;

import java.time.Instant;
import java.util.UUID;

public record TenantUserResponseDTO(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String jobTitle,
        String department,
        TenantRole role,
        MembershipStatus status,
        Instant membershipCreatedAt,
        Instant membershipUpdatedAt
) {
}
