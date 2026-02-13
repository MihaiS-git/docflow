package com.brutecx.docflow_backend.api.dto.tenant;

import com.brutecx.docflow_backend.domain.tenant.UserTenantMembership;
import com.brutecx.docflow_backend.domain.user.User;

public final class TenantUserResponseMapper {

    private TenantUserResponseMapper() {
    }

    public static TenantUserResponseDTO from(UserTenantMembership m) {
        User u = m.getUser();
        return new TenantUserResponseDTO(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getDisplayName(),
                u.getJobTitle(),
                u.getDepartment(),
                m.getRole(),
                m.getStatus(),
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
