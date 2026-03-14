package com.brutecx.docflow_backend.api.dto.tenant;

import com.brutecx.docflow_backend.domain.tenant.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record UserTenantListItemDTO(

        UUID id,
        String name,
        TenantStatus status,

        String dataRegion,
        Long retentionDays,

        Instant createdAt,
        Instant updatedAt
) {}