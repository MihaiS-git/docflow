package com.brutecx.docflow_backend.api.dto.admin.tenant;

public record UpdateTenantRequest(
        String name,
        String description,
        String dataRegion,
        Long retentionDays,
        Boolean disableBootstrap,
        String comment
) {
}
