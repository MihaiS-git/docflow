package com.brutecx.docflow_backend.api.dto.tenant;

import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembership;
import com.brutecx.docflow_backend.domain.user.User;

import java.time.Instant;
import java.util.UUID;

public record TenantUserResponseDTO(
        UUID userId,
        String email,
        String businessPhone,
        String firstName,
        String lastName,
        String displayName,
        String jobTitle,
        String department,
        TenantRole role,
        MembershipStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public TenantUserResponseDTO(
            UUID userId,
            String email,
            String businessPhone,
            String firstName,
            String lastName,
            String displayName,
            String jobTitle,
            String department,
            TenantRole role,
            MembershipStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.email = email;
        this.businessPhone = businessPhone;
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.jobTitle = jobTitle;
        this.department = department;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TenantUserResponseDTO from(UserTenantMembership m) {
        User u = m.getUser();
        return new TenantUserResponseDTO(
                u.getId(),
                u.getEmail(),
                u.getBusinessPhone(),
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