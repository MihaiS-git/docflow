package com.brutecx.docflow_backend.api.dto.tenant;

import jakarta.validation.constraints.NotBlank;

public record UpdateTenantRequest(
        @NotBlank String name,
        String dataRegion,
        Long retentionDays,
        Boolean bootstrapEnabled
) {
}
