package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Hard authorization boundary for tenant-level MANAGER access.
 * SECURITY PROPERTIES:
 * - Tenant must be ACTIVE
 * - Membership must be ACTIVE
 * - Role must be at least MANAGER
 * No silent fallback.
 * No privilege escalation through ordering.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantRoleGuard {

    private final UserService userService;
    private final UserTenantMembershipRepository membershipRepository;

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

        // Tenant lifecycle boundary
        if (membership.getTenant().getStatus() != TenantStatus.ACTIVE) {
            throw new AccessDeniedException("Tenant is not ACTIVE");
        }

        // Membership lifecycle boundary
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("Membership is not ACTIVE");
        }

        // Privilege boundary (explicit level-based hierarchy)
        if (!membership.getRole().isAtLeast(TenantRole.MANAGER)) {
            throw new AccessDeniedException("Requires MANAGER role in tenant");
        }
    }
}