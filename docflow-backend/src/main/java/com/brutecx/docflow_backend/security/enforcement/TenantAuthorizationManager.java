package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembership;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembershipRepository;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import com.brutecx.docflow_backend.logging.InfraEventActions;
import com.brutecx.docflow_backend.logging.InfraEventLogger;
import com.brutecx.docflow_backend.logging.InfraEventOutcome;
import com.brutecx.docflow_backend.logging.InfraEventType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.argument.StructuredArguments;
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

@RequiredArgsConstructor
public class TenantAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Pattern TENANT_PATH =
            Pattern.compile("^/api/tenants/([0-9a-fA-F\\-]{36})(?:/|$)");

    private final TenantRole requiredRole;
    private final UserRepository userRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final ILifecycleDeniedAuditService lifecycleDeniedAuditService;

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {

        HttpServletRequest request = context.getRequest();
        String uri = request.getRequestURI();
        String method = request.getMethod();

        Optional<UUID> tenantIdOpt = extractTenantId(uri);

        if (tenantIdOpt.isEmpty()) {
            return new AuthorizationDecision(true);
        }

        UUID tenantId = tenantIdOpt.get();
        Authentication auth = authenticationSupplier.get();

        if (auth == null || !auth.isAuthenticated()) {
            audit(null, enrich("AUTH_NOT_AUTHENTICATED", tenantId), method, uri, null);
            return new AuthorizationDecision(false);
        }

        if (!(auth.getPrincipal() instanceof OidcUser oidcUser)) {
            audit(null, enrich("AUTH_PRINCIPAL_NOT_OIDC", tenantId), method, uri, null);
            return new AuthorizationDecision(false);
        }

        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            audit(null, enrich("AUTH_SUBJECT_MISSING", tenantId), method, uri, null);
            return new AuthorizationDecision(false);
        }

        User user = userRepository.findByExternalSubjectId(subject).orElse(null);
        if (user == null) {
            audit(subject, enrich("AUTH_LOCAL_USER_NOT_FOUND", tenantId), method, uri, null);
            return new AuthorizationDecision(false);
        }

        UserTenantMembership membership =
                membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)
                        .orElse(null);

        if (membership == null) {
            audit(subject, enrich("TENANT_MEMBERSHIP_MISSING", tenantId), method, uri,
                    "requiredRole=" + requiredRole);
            return new AuthorizationDecision(false);
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            audit(subject, enrich("TENANT_MEMBERSHIP_NOT_ACTIVE", tenantId), method, uri,
                    "status=" + membership.getStatus() + ";requiredRole=" + requiredRole);
            return new AuthorizationDecision(false);
        }

        TenantRole actualRole = membership.getRole();
        if (actualRole == null || !actualRole.isAtLeast(requiredRole)) {
            audit(subject, enrich("TENANT_ROLE_INSUFFICIENT", tenantId), method, uri,
                    "actualRole=" + actualRole + ";requiredRole=" + requiredRole);
            return new AuthorizationDecision(false);
        }

        return new AuthorizationDecision(true);
    }

    private void audit(String subjectId, String reasonCode, String method, String uri, String detail) {

        try {
            lifecycleDeniedAuditService.record(
                    subjectId,
                    reasonCode,
                    method,
                    uri,
                    detail
            );

            InfraEventLogger.log(
                    InfraEventType.AUTHORIZATION,
                    InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                    InfraEventOutcome.SUCCESS,
                    null,
                    null,
                    StructuredArguments.kv("actor.subject_id", subjectId),
                    StructuredArguments.kv("http.method", method),
                    StructuredArguments.kv("http.path", uri)
            );

        } catch (Exception ex) {
            InfraEventLogger.log(
                    InfraEventType.AUTHORIZATION,
                    InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                    InfraEventOutcome.FAILURE,
                    "LifecycleDenied audit write failed",
                    ex
            );
        }
    }

    private static String enrich(String baseReason, UUID tenantId) {
        return baseReason + ":" + tenantId;
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