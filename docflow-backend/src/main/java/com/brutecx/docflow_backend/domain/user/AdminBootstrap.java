package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the single initial full-access admin user at application startup.
 * Hard constraints:
 * - Invite-only forever (no auto-registration on login).
 * - externalSubjectId must be NULL at creation time and bound exactly once on first successful login.
 * - Tenant already exists (bootstrapped separately).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
@Profile({"dev", "prod"})
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final TenantService tenantService;

    @Value("${docflow.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${docflow.bootstrap.admin.first-name:}")
    private String adminFirstName;

    @Value("${docflow.bootstrap.admin.last-name:}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            throw new IllegalStateException("Missing required property: docflow.bootstrap.admin.email");
        }
        if (adminFirstName == null || adminFirstName.isBlank()) {
            throw new IllegalStateException("Missing required property: docflow.bootstrap.admin.first-name");
        }
        if (adminLastName == null || adminLastName.isBlank()) {
            throw new IllegalStateException("Missing required property: docflow.bootstrap.admin.last-name");
        }

        Tenant tenant = tenantService.getCurrentTenant();

        // Ensure the admin exists for this tenant (do NOT depend on Keycloak login).
        boolean exists = userRepository.existsByTenantIdAndEmailIgnoreCase(tenant.getId(), adminEmail);
        if (exists) {
            return;
        }

        User admin = new User(
                adminEmail,
                adminFirstName,
                adminLastName,
                null,
                null
        );

        // externalSubjectId MUST remain NULL until first successful login
        admin.activate();

        tenant.addUser(admin);
        userRepository.save(admin);

        log.info("Bootstrapped initial admin local user for tenantId={} email={}", tenant.getId(), adminEmail);
    }
}
