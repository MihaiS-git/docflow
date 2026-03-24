package com.brutecx.docflow_backend.api.dto.admin.tenant;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
        @NotBlank String name,
        String description,
        String dataRegion,
        Integer retentionDays
) {
}