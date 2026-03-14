package com.brutecx.docflow_backend.api.dto.admin.tenant;

import com.brutecx.docflow_backend.domain.tenant.TenantStatus;

import java.util.UUID;

public record TenantLookupDTO(
        UUID id,
        String name,
        TenantStatus status
) {}