package com.brutecx.docflow_backend.api.dto.tenant;

import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import jakarta.validation.constraints.NotBlank;

public record UpdateMembershipRequest(
        TenantRole role,
        MembershipStatus status,
        @NotBlank String comment
) {
}
