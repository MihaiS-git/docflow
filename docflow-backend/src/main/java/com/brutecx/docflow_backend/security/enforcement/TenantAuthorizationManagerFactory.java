package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembershipRepository;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Factory for tenant-scoped authorization managers.
 * Keeps SecurityConfig wiring clean without reflection/dynamic authorities.
 */
@Component
@RequiredArgsConstructor
public class TenantAuthorizationManagerFactory {

    private final UserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;

    public AuthorizationManager<RequestAuthorizationContext> atLeast(TenantRole requiredRole) {
        return new TenantAuthorizationManager(requiredRole, userRepository, membershipRepository);
    }
}
