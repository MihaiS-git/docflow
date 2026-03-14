package com.brutecx.docflow_backend.api.dto.admin.tenant;

import com.brutecx.docflow_backend.domain.tenant.TenantStatus;

import java.time.LocalDate;

public record TenantFilter(
        TenantStatus status,
        String name,
        String dataRegion,
        String managerName,
        String managerEmail,
        LocalDate createdAfter,
        LocalDate createdBefore
) {}