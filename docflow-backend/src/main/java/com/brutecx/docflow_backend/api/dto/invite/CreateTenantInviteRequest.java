package com.brutecx.docflow_backend.api.dto.invite;

import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tenant-scoped invite request.
 * Tenant is taken from the path (/api/tenants/{tenantId}/...).
 */
public record CreateTenantInviteRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 255)
        String firstName,

        @NotBlank
        @Size(max = 255)
        String lastName,

        @Size(max = 255)
        String jobTitle,

        @Size(max = 255)
        String department,

        @NotNull
        TenantRole tenantRole
) {
}


