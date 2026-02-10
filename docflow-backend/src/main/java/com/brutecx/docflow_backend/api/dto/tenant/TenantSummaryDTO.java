package com.brutecx.docflow_backend.api.dto.tenant;

import com.brutecx.docflow_backend.domain.tenant.TenantStatus;

import java.util.UUID;

public record TenantSummaryDTO(
        UUID id,
        String name,
        TenantStatus status
) {
}
