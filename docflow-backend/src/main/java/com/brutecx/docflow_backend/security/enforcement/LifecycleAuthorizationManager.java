package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.api.error.LifecycleAccessDeniedException;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.tenant.TenantStatus;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import com.brutecx.docflow_backend.domain.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Authorization manager that enforces tenant and user lifecycle status checks.
 * Throws LifecycleAccessDeniedException if access is denied due to lifecycle status.
 * Applies only to authenticated human users (OidcUser).
 * Checks:
 * 1. Tenant must not be SUSPENDED.
 * 2. User must be ACTIVE.
 * If not authenticated or not a human user, allows access to let other mechanisms decide.
 */

@SuppressWarnings("deprecation")
@Slf4j
@Component
@RequiredArgsConstructor
public class LifecycleAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final TenantService tenantService;
    private final UserRepository userRepository;

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication authentication = authenticationSupplier.get();
        String uri = context.getRequest().getRequestURI();

        // ADDED: allow invite bootstrap endpoints unconditionally
        if (uri.startsWith("/api/invites/")) {
            return new AuthorizationDecision(true);
        }


        if (authentication != null) {
            log.debug(
                    "SECURITY DEBUG → uri={}, authorities={}",
                    uri,
                    authentication.getAuthorities()
            );
        }

        // Not authenticated → let other mechanisms decide
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(true);
        }

        // Only enforce for real human users
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return new AuthorizationDecision(true);
        }

        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        log.info("Lifecycle check: subject={}, email={}, uri={}", subject, email, uri);

        // 1. Tenant lifecycle
        Tenant tenant;
        try {
            tenant = tenantService.getCurrentTenant();
        } catch (Exception ex) {
            log.warn(
                    "LIFECYCLE DENIED → tenant resolution failed for principal subject={} email={} uri={}",
                    subject,
                    email,
                    uri
            );
            throw new LifecycleAccessDeniedException(
                    "TENANT_RESOLUTION_FAILED",
                    "Tenant resolution failed"
            );
        }

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            log.warn(
                    "LIFECYCLE DENIED → tenant suspended for principal subject={} email={} tenantId={}",
                    subject,
                    email,
                    tenant.getId()
            );
            throw new LifecycleAccessDeniedException(
                    "TENANT_SUSPENDED",
                    "Tenant is suspended"
            );
        }

        // 2. User lifecycle
        User user = userRepository
                .findByExternalSubjectId(subject)
                .orElse(null);

        log.info("Resolved user for lifecycle check: {}", user);

        if (user == null) {
            log.warn(
                    "LIFECYCLE DENIED → local user missing for principal subject={} email={} uri={}",
                    subject,
                    email,
                    uri
            );
            throw new LifecycleAccessDeniedException(
                    "LOCAL_USER_MISSING",
                    "Authenticated subject not mapped to a local user"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn(
                    "LIFECYCLE DENIED → user inactive for principal subject={} email={} status={}",
                    subject,
                    email,
                    user.getStatus()
            );
            throw new LifecycleAccessDeniedException(
                    "USER_NOT_ACTIVE",
                    "User is not active"
            );
        }

        return new AuthorizationDecision(true);
    }

}
