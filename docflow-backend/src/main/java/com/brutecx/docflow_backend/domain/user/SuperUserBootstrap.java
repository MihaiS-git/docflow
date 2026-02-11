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
 * Bootstraps the initial superuser at application startup.
 * Invariants:
 * - Invite-only forever (no auto-registration on login).
 * - externalSubjectId is NULL at bootstrap time.
 * - User is created in LOCKED state.
 * - Identity binding + activation happen ONLY via an explicit bootstrap-claim flow.
 * - Tenant already exists (bootstrapped separately).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
@Profile({"dev", "prod"})
public class SuperUserBootstrap implements ApplicationRunner {

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

        if (adminEmail == null || adminEmail.isBlank()
                || adminFirstName == null || adminFirstName.isBlank()
                || adminLastName == null || adminLastName.isBlank()) {

            log.warn("Bootstrap skipped — admin properties not configured");
            return;
        }

        // If no tenant exists → bootstrap tenant first
        Tenant tenant = tenantService.getOrCreateBootstrapTenant();

        boolean exists =
                userRepository.existsByTenantIdAndEmailIgnoreCase(
                        tenant.getId(),
                        adminEmail
                );

        if (exists) {
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

        tenant.addUser(admin);
        userRepository.save(admin);

        log.info(
                "Bootstrapped superuser (LOCKED) tenantId={} email={}",
                tenant.getId(),
                adminEmail
        );
    }

}
