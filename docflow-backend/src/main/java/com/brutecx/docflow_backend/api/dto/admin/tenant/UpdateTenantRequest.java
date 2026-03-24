package com.brutecx.docflow_backend.api.dto.admin.tenant;

public record UpdateTenantRequest(
        String name,
        String description,
        String dataRegion,
        Integer retentionDays,
        Boolean disableBootstrap,
        String comment
) {
}
