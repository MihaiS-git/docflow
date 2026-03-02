package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantMembershipService;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
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
    private final TenantMembershipService tenantMembershipService;

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

        // Every user must belong to ROOT tenant (baseline MEMBER)
        tenantMembershipService.ensureMembership(
                user.getId(),
                tenantService.getRootTenant().getId(),
                TenantRole.MEMBER
        );

        // Ensure invited tenant membership (baseline MEMBER)
        tenantMembershipService.ensureMembership(
                user.getId(),
                tenant.getId(),
                TenantRole.MEMBER
        );

        return user;
    }
}