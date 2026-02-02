package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
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

        if (userRepository.existsByTenantIdAndEmailIgnoreCase(
                tenant.getId(), normalizedEmail)) {
            throw new IllegalStateException(
                    "User already exists in tenant for email " + normalizedEmail);
        }

        User user = new User(
                normalizedEmail,
                firstName,
                lastName,
                jobTitle,
                department
        );

        log.info("Provisioning user {} into tenant {}", user, tenant);

        tenant.addUser(user); // invariant enforced here

        log.info("Provisioned user {} into tenant {}", user, tenant);

        return userRepository.save(user);
    }

}
