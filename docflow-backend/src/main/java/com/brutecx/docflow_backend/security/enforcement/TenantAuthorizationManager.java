package com.brutecx.docflow_backend.security.enforcement;

import com.brutecx.docflow_backend.audit.lifecycle.ILifecycleDeniedAuditService;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleAuditMetadata;
import com.brutecx.docflow_backend.audit.lifecycle.TenantAuthorizationMetadata;
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
            audit(
                    null,
                    "AUTH_NOT_AUTHENTICATED",
                    method,
                    uri,
                    tenantAuthMetadata(tenantId, requiredRole, null, null)
            );
            return new AuthorizationDecision(false);
        }

        if (!(auth.getPrincipal() instanceof OidcUser oidcUser)) {
            audit(
                    null,
                    "AUTH_PRINCIPAL_NOT_OIDC",
                    method,
                    uri,
                    tenantAuthMetadata(tenantId, requiredRole, null, null)
            );
            return new AuthorizationDecision(false);
        }

        String subject = oidcUser.getSubject();
        if (subject == null || subject.isBlank()) {
            audit(
                    null,
                    "AUTH_SUBJECT_MISSING",
                    method,
                    uri,
                    tenantAuthMetadata(tenantId, requiredRole, null, null)
            );
            return new AuthorizationDecision(false);
        }

        User user = userRepository.findByExternalSubjectId(subject).orElse(null);
        if (user == null) {
            audit(
                    subject,
                    "AUTH_LOCAL_USER_NOT_FOUND",
                    method,
                    uri,
                    tenantAuthMetadata(tenantId, requiredRole, null, null)
            );
            return new AuthorizationDecision(false);
        }

        UserTenantMembership membership =
                membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId)
                        .orElse(null);

        if (membership == null) {
            audit(
                    subject,
                    "TENANT_MEMBERSHIP_MISSING",
                    method,
                    uri,
                    tenantAuthMetadata(tenantId, requiredRole, null, null)
            );
            return new AuthorizationDecision(false);
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            audit(
                    subject,
                    "TENANT_MEMBERSHIP_NOT_ACTIVE",
                    method,
                    uri,
                    tenantAuthMetadata(
                            tenantId,
                            requiredRole,
                            membership.getRole(),
                            membership.getStatus()
                    )
            );
            return new AuthorizationDecision(false);
        }

        TenantRole actualRole = membership.getRole();
        if (actualRole == null || !actualRole.isAtLeast(requiredRole)) {
            audit(
                    subject,
                    "TENANT_ROLE_INSUFFICIENT",
                    method,
                    uri,
                    tenantAuthMetadata(
                            tenantId,
                            requiredRole,
                            actualRole,
                            membership.getStatus()
                    )
            );
            return new AuthorizationDecision(false);
        }

        return new AuthorizationDecision(true);
    }

    private void audit(
            String subjectId,
            String reasonCode,
            String method,
            String uri,
            LifecycleAuditMetadata metadata
    ) {
        try {
            lifecycleDeniedAuditService.record(
                    subjectId,
                    reasonCode,
                    method,
                    uri,
                    metadata
            );

            InfraEventLogger.log(
                    InfraEventType.AUTHORIZATION,
                    InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                    InfraEventOutcome.SUCCESS,
                    null,
                    null,
                    StructuredArguments.kv("actor.subject_id", subjectId),
                    StructuredArguments.kv("http.method", method),
                    StructuredArguments.kv("http.path", uri),
                    StructuredArguments.kv("lifecycle.reason_code", reasonCode)
            );

        } catch (Exception ex) {
            InfraEventLogger.log(
                    InfraEventType.AUTHORIZATION,
                    InfraEventActions.AUTHZ_LIFECYCLE_DENIED_AUDIT_WRITE,
                    InfraEventOutcome.FAILURE,
                    "LifecycleDenied audit write failed",
                    ex,
                    StructuredArguments.kv("actor.subject_id", subjectId),
                    StructuredArguments.kv("http.method", method),
                    StructuredArguments.kv("http.path", uri),
                    StructuredArguments.kv("lifecycle.reason_code", reasonCode)
            );
        }
    }

    private static TenantAuthorizationMetadata tenantAuthMetadata(
            UUID tenantId,
            TenantRole requiredRole,
            TenantRole actualRole,
            MembershipStatus membershipStatus
    ) {
        return new TenantAuthorizationMetadata(
                tenantId.toString(),
                requiredRole != null ? requiredRole.name() : null,
                actualRole != null ? actualRole.name() : null,
                membershipStatus != null ? membershipStatus.name() : null
        );
    }

    private static Optional<UUID> extractTenantId(String uri) {
        if (uri == null || uri.isBlank()) {
            return Optional.empty();
        }

        Matcher m = TENANT_PATH.matcher(uri);
        if (!m.find()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(m.group(1)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}