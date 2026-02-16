package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantRoleGuard {

    private final UserService userService;
    private final UserTenantMembershipRepository membershipRepository;

    /**
     * Enforces that the current authenticated user:
     *  - is a member of the tenant
     *  - membership is ACTIVE
     *  - has TenantRole.MANAGER
     *
     * Hard boundary check. Auditor-friendly.
     */
    public void requireTenantManager(UUID tenantId) {

        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        User actor = userService.getRequiredCurrentUser();

        UserTenantMembership membership =
                membershipRepository.findByUserIdAndTenantId(actor.getId(), tenantId)
                        .orElseThrow(() ->
                                new AccessDeniedException("Not a member of tenant")
                        );

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("Membership is not ACTIVE");
        }

        if (membership.getRole() != TenantRole.MANAGER) {
            throw new AccessDeniedException("Requires MANAGER role in tenant");
        }
    }
}
