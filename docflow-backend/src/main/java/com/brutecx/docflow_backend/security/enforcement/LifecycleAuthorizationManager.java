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
        return authorizeInternal(authenticationSupplier, context);
    }

    private AuthorizationDecision authorizeInternal(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication authentication = authenticationSupplier.get();
        String uri = context.getRequest().getRequestURI();

        if (uri.startsWith("/api/invites/")) {
            return new AuthorizationDecision(true);
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(true);
        }

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return new AuthorizationDecision(true);
        }

        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();

        Tenant tenant = tenantService.getCurrentTenant();

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new LifecycleAccessDeniedException(
                    "TENANT_SUSPENDED",
                    "Tenant is suspended"
            );
        }

        User user = userRepository.findByExternalSubjectId(subject)
                .orElseGet(() -> userRepository
                        .findByTenantIdAndEmailIgnoreCase(tenant.getId(), email)
                        .orElse(null));

        if (user == null) {
            throw new LifecycleAccessDeniedException(
                    "LOCAL_USER_MISSING",
                    "Authenticated subject not mapped to a local user"
            );
        }

        if (user.getExternalSubjectId() != null &&
                !user.getExternalSubjectId().equals(subject)) {
            throw new LifecycleAccessDeniedException(
                    "SUBJECT_MISMATCH",
                    "Authenticated subject does not match the bound local user"
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new LifecycleAccessDeniedException(
                    "USER_NOT_ACTIVE",
                    "User is not active"
            );
        }

        return new AuthorizationDecision(true);
    }

}
