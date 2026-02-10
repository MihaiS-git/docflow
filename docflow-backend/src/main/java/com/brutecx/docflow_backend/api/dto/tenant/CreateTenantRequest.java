package com.brutecx.docflow_backend.api.dto.tenant;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
        @NotBlank String name
) {
}
