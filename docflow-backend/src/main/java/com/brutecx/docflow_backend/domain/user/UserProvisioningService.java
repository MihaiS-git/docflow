package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembership;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService implements IUserProvisioningService {

    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final UserTenantMembershipRepository membershipRepository;

    @Override
    @Transactional
    public User provisionInvitedUser(
            Tenant tenant,
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String department
    ) {

        String normalizedEmail = email.toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> userRepository.save(new User(
                        normalizedEmail,
                        firstName,
                        lastName,
                        jobTitle,
                        department
                )));

        // Every user must always belong to ROOT tenant
        ensureMembership(user, tenantService.getRootTenant());

        // Ensure invited tenant membership (baseline MEMBER)
        ensureMembership(user, tenant);

        return user;
    }

    private void ensureMembership(User user, Tenant tenant) {

        if (membershipRepository.existsByUserIdAndTenantId(
                user.getId(),
                tenant.getId()
        )) {
            return;
        }

        UserTenantMembership.create(
                user,
                tenant,
                TenantRole.MEMBER
        );
    }
}
