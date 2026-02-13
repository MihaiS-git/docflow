package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembership;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembershipRepository;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contextual tenant RBAC authorization.
 * <p>
 * Rules:
 * - Extract tenantId from request path /api/tenants/{tenantId}/...
 * - Resolve local user by OIDC subject (externalSubjectId)
 * - Load UserTenantMembership(userId, tenantId)
 * - Deny if missing or not ACTIVE
 * - Deny if role is insufficient
 */
@RequiredArgsConstructor
public class TenantAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Pattern TENANT_PATH =
            Pattern.compile("^/api/tenants/([0-9a-fA-F\\-]{36})(?:/|$)");

    private final TenantRole requiredRole;
    private final UserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;


    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication auth = authenticationSupplier.get();
        String uri = context.getRequest().getRequestURI();

        // If this is not a tenant-scoped path, allow (do not interfere).
        Optional<UUID> tenantIdOpt = extractTenantId(uri);
        if (tenantIdOpt.isEmpty()) {
            return new AuthorizationDecision(true);
        }

        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        if (!(auth.getPrincipal() instanceof OidcUser oidcUser)) {
            return new AuthorizationDecision(false);
        }

        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            return new AuthorizationDecision(false);
        }

        User user = userRepository.findByExternalSubjectId(subject)
                .orElse(null);
        if (user == null) {
            return new AuthorizationDecision(false);
        }

        UUID tenantId = tenantIdOpt.get();

        UserTenantMembership membership =
                membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)
                        .orElse(null);

        if (membership == null) {
            return new AuthorizationDecision(false);
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return new AuthorizationDecision(false);
        }

        TenantRole role = membership.getRole();
        if (role == null) {
            return new AuthorizationDecision(false);
        }

        boolean allowed = role.isAtLeast(requiredRole);
        return new AuthorizationDecision(allowed);
    }

    private static Optional<UUID> extractTenantId(String uri) {
        if (uri == null || uri.isBlank()) return Optional.empty();
        Matcher m = TENANT_PATH.matcher(uri);
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(m.group(1)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
