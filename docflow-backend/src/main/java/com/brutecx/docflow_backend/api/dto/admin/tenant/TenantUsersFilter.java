package com.brutecx.docflow_backend.api.dto.admin.tenant;

import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;

import java.time.LocalDate;

public record TenantUsersFilter (
        String displayName,
        String userEmail,
        String jobTitle,
        String department,
        TenantRole role,
        MembershipStatus status,
        LocalDate createdAfter,
        LocalDate createdBefore
){
}
