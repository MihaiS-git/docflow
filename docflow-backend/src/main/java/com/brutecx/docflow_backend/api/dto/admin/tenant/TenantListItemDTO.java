package com.brutecx.docflow_backend.api.dto.admin.tenant;

import com.brutecx.docflow_backend.domain.tenant.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantListItemDTO(
        UUID id,
        String name,
        String description,

        UUID ownerId,

        TenantStatus status,

        String managerName,
        String managerEmail,

        Long membersCount,

        String dataRegion,
        Integer retentionDays,

        Instant lastActivity,

        Instant createdAt,
        Instant updatedAt
) {
}