package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.tenant.Tenant;
import com.brutecx.docflow_backend.tenant.TenantService;
import com.brutecx.docflow_backend.tenant.TenantStatus;
import com.brutecx.docflow_backend.user.User;
import com.brutecx.docflow_backend.user.UserRepository;
import com.brutecx.docflow_backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

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

        // Not authenticated → let other mechanisms decide
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(true);
        }

        // Only enforce for real human users
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return new AuthorizationDecision(true);
        }

        // 1. Tenant lifecycle
        Tenant tenant = tenantService.getCurrentTenant();
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new LifecycleAccessDeniedException(
                    "TENANT_SUSPENDED",
                    "Tenant is suspended"
            );
        }

        // 2. User lifecycle
        String subject = oidcUser.getSubject();

        User user = userRepository
                .findByExternalSubjectId(subject)
                .orElseThrow(() ->
                        new LifecycleAccessDeniedException(
                                "USER_NOT_FOUND",
                                "Local user not found"
                        )
                );

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new LifecycleAccessDeniedException(
                    "USER_" + user.getStatus().name(),
                    "User is " + user.getStatus().name().toLowerCase()
            );
        }

        return new AuthorizationDecision(true);
    }

}
