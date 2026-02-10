package com.brutecx.docflow_backend.api.dto.admin.tenant;

import com.brutecx.docflow_backend.domain.tenant.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantListItemDTO(
        UUID id,
        String name,
        TenantStatus status,
        String dataRegion,
        Long retentionDays,
        boolean bootstrapEnabled,
        Instant createdAt,
        Instant updatedAt
) {}
