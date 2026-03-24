package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantMembershipService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;

    @Transactional
    public void ensureMembership(UUID userId, UUID tenantId, TenantRole role) {

        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(role, "role");

        User user = userRepository.getReferenceById(userId);
        Tenant tenant = tenantRepository.getReferenceById(tenantId);

        membershipRepository.findByUserIdAndTenantId(userId, tenantId)
                .ifPresentOrElse(existing -> {

                    if (existing.getRole() == null || role.isMorePrivilegedThan(existing.getRole())) {
                        existing.changeRole(role);
                    }

                    if (existing.getStatus() != MembershipStatus.ACTIVE) {
                        existing.activate();
                    }

                }, () -> membershipRepository.save(
                        UserTenantMembership.create(user, tenant, role)
                ));
    }
}