package com.brutecx.docflow_backend.api.dto.invite;

import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Tenant-scoped invite request.
 * Tenant is taken from the path (/api/tenants/{tenantId}/...).
 */
public record CreateTenantInviteRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String firstName,

        @NotBlank
        String lastName,

        String jobTitle,

        String department,

        TenantRole tenantRole
) {
}


