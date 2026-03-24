package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
@Profile({"dev", "prod"})
public class SuperUserBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final TenantMembershipService tenantMembershipService;

    @Value("${docflow.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${docflow.bootstrap.admin.first-name:}")
    private String adminFirstName;

    @Value("${docflow.bootstrap.admin.last-name:}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        if (adminEmail == null || adminEmail.isBlank()
                || adminFirstName == null || adminFirstName.isBlank()
                || adminLastName == null || adminLastName.isBlank()) {

            log.warn("Bootstrap skipped — admin properties not configured");
            return;
        }

        Tenant root = tenantService.getOrCreateBootstrapTenant();

        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            log.info("Bootstrap admin already exists — skipping");
            return;
        }

        User admin = new User(
                adminEmail,
                adminFirstName,
                adminLastName,
                "SuperUser",
                "Admin"
        );

        userRepository.save(admin);

        // Ensure bootstrap admin has ROOT MANAGER membership
        tenantMembershipService.ensureMembership(
                admin.getId(),
                root.getId(),
                TenantRole.MANAGER
        );

        tenantService.assignOwner(root.getId(), admin.getId());

        log.info(
                "Bootstrapped superuser (LOCKED) rootTenantId={} email={}",
                root.getId(),
                adminEmail
        );
    }
}