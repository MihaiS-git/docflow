package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.api.error.LifecycleAccessDeniedException;
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

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class LifecycleAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final UserRepository userRepository;
    private final TenantService tenantService;

    private static final Pattern TENANT_PATH =
            Pattern.compile("^/api/tenants/([0-9a-fA-F\\-]{36})(?:/|$)");

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

        if (uri.equals("/api/auth/me")
                || uri.equals("/api/users/me")
                || uri.equals("/api/bootstrap/activate")) {
            return new AuthorizationDecision(true);
        }

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

        User user = userRepository.findByExternalSubjectId(subject)
                .orElseThrow(() ->
                        new LifecycleAccessDeniedException(
                                "LOCAL_USER_MISSING",
                                "Authenticated subject not mapped to a local user"
                        )
                );

        UUID tenantId = resolveTenantIdForLifecycle(uri, tenantService);
        TenantStatus tenantStatus = tenantService.getRequiredTenantStatus(tenantId);

        if (tenantStatus == TenantStatus.SUSPENDED) {
            throw new LifecycleAccessDeniedException(
                    "TENANT_SUSPENDED",
                    "Tenant is suspended"
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

    private static UUID resolveTenantIdForLifecycle(String uri, TenantService tenantService) {
        Optional<UUID> pathTenant = extractTenantId(uri);
        if (pathTenant.isPresent()) {
            return pathTenant.get();
        }
        return tenantService.getRootTenant().getId();
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
