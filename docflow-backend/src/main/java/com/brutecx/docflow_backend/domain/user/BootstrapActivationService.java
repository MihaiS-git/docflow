package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.api.error.BootstrapActivationDeniedException;
import com.brutecx.docflow_backend.api.error.BootstrapActivationNotAllowedException;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.UserStateChangeMetadata;
import com.brutecx.docflow_backend.audit.admin.UserStateChangeReason;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BootstrapActivationService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final IAdminAuditEventService adminAuditEventService;

    @Value("${docflow.bootstrap.admin.email:}")
    private String bootstrapAdminEmail;

    @Transactional
    public void activateBootstrapAdmin() {

        OidcUser oidcUser = requireOidcUser();

        String expectedEmail = normalizeEmail(bootstrapAdminEmail);
        if (expectedEmail == null || expectedEmail.isBlank()) {
            throw new IllegalStateException("Missing required property: docflow.bootstrap.admin.email");
        }

        String oidcEmail = normalizeEmail(oidcUser.getEmail());

        if (oidcEmail == null || !oidcEmail.equals(expectedEmail)) {
            throw new BootstrapActivationDeniedException(
                    "Bootstrap activation is restricted to the configured bootstrap admin email"
            );
        }

        Tenant tenant = tenantRepository.findFirstByTenantType(com.brutecx.docflow_backend.domain.tenant.TenantType.ROOT)
                .orElseThrow(() -> new IllegalStateException("No ROOT tenant exists for bootstrap activation"));

        if (!tenant.isBootstrapEnabled()) {
            throw new BootstrapActivationNotAllowedException("Bootstrap is already disabled");
        }

        boolean anyActive =
                userRepository.findAll().stream().anyMatch(u -> u.getStatus() == UserStatus.ACTIVE);

        if (anyActive) {
            throw new BootstrapActivationNotAllowedException(
                    "Bootstrap activation is not allowed once ACTIVE users exist"
            );
        }

        User admin = userRepository.findByEmailIgnoreCase(expectedEmail)
                .orElseThrow(() ->
                        new BootstrapActivationNotAllowedException(
                                "Bootstrap admin local user row is missing; SuperUserBootstrap must run first"
                        )
                );

        if (admin.getStatus() != UserStatus.LOCKED) {
            throw new BootstrapActivationNotAllowedException("Bootstrap admin is not in LOCKED state");
        }

        if (admin.getExternalSubjectId() != null) {
            throw new BootstrapActivationNotAllowedException("Bootstrap admin is already bound to an external subject");
        }

        AuditRequestContext ctx = auditRequestContextExtractor.fromCurrentRequest();
        if (ctx.correlationId() == null || ctx.correlationId().isBlank()) {
            throw new IllegalStateException("Missing correlationId in request context");
        }

        // Perform activation
        admin.bindExternalSubjectId(oidcUser.getSubject());
        admin.activate();
        userRepository.save(admin);

        tenant.disableBootstrap();
        tenantRepository.save(tenant);

        // Centralized audit (new invariant signature)
        adminAuditEventService.record(
                AdminAuditActionType.BOOTSTRAP_ACTIVATED,
                tenant.getId(),
                oidcUser.getSubject(),
                admin.getId(),
                new UserStateChangeMetadata(
                        UserStateChangeReason.BOOTSTRAP_ACTIVATION,
                        null
                )
        );
    }

    private Tenant getSingleTenantForBootstrap() {
        long count = tenantRepository.count();

        if (count == 0) {
            throw new IllegalStateException(
                    "No tenant exists. Tenant bootstrap must run before admin bootstrap."
            );
        }

        if (count > 1) {
            throw new IllegalStateException(
                    "Multiple tenants exist (" + count + "). Bootstrap activation requires exactly one tenant."
            );
        }

        return tenantRepository.findAll().getFirst();
    }

    private static OidcUser requireOidcUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BootstrapActivationDeniedException("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OidcUser oidcUser)) {
            throw new BootstrapActivationDeniedException("OIDC authentication required");
        }

        return oidcUser;
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return e.isBlank() ? null : e;
    }
}
