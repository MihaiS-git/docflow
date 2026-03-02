package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.api.error.InviteNotFoundException;
import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.audit.admin.*;
import com.brutecx.docflow_backend.audit.metrics.IdentityAnomalyMetrics;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditService;
import com.brutecx.docflow_backend.domain.invite.Invite;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.tenant.UserTenantMembershipRepository;
import com.brutecx.docflow_backend.domain.user.IUserProvisioningService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.brutecx.docflow_backend.domain.user.UserStatus;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteApplicationService {

    @Value("${docflow.security.frontend-base-url}")
    private String frontendBaseUrl;

    private final InviteRepository inviteRepository;
    private final IMailService mailService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final TenantService tenantService;
    private final IUserProvisioningService userProvisioningService;
    private final IAdminAuditEventService adminAuditEventService;
    private final OnboardingAuditService onboardingAuditService;
    private final UserService userService;
    private final UserTenantMembershipRepository membershipRepository;
    private final IdentityAnomalyMetrics identityAnomalyMetrics;
    private final SessionRegistry sessionRegistry;

    @Transactional
    public void createAndSendInvite(
            UUID targetTenantId,
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String department,
            TenantRole tenantRole
    ) {
        if (tenantRole != null && targetTenantId == null) {
            throw new IllegalArgumentException(
                    "tenantRole cannot be provided without targetTenantId"
            );
        }

        Tenant tenant = tenantService.getRequired(targetTenantId);
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        var actor = userService.getRequiredCurrentUser();

        boolean success = false;
        Invite invite = null;
        String keycloakUserId = null;

        try {
            User user = userProvisioningService.provisionInvitedUser(
                    tenant,
                    normalizedEmail,
                    firstName,
                    lastName,
                    jobTitle,
                    department
            );

            TenantRole effectiveRole = (tenantRole != null)
                    ? tenantRole
                    : TenantRole.MEMBER;

            membershipRepository.findByUserIdAndTenantId(user.getId(), tenant.getId())
                    .ifPresent(m -> m.changeRole(effectiveRole));

            invite = Invite.create(normalizedEmail, tenant.getId(), effectiveRole);
            invite.linkUser(user);
            inviteRepository.save(invite);

            String temporaryPassword = generateTemporaryPassword();

            keycloakUserId =
                    keycloakAdminClient.ensureInviteUserExistsWithRequiredActionsAndTempPassword(
                            normalizedEmail,
                            temporaryPassword
                    );

            // Bind subject (strict mismatch enforcement)
            String existingSubject = user.getExternalSubjectId();
            if (existingSubject != null && !existingSubject.equals(keycloakUserId)) {
                identityAnomalyMetrics.incrementBindFailure(tenant.getId().toString());

                adminAuditEventService.record(
                        AdminAuditActionType.INVITE_SUBJECT_BIND_FAILED,
                        tenant.getId(),
                        actor.getExternalSubjectId(),
                        user.getId(),
                        new InviteSubjectBindingAuditMetadata(
                                user.getId(),
                                null,
                                sha256Hex(normalizedEmail)
                        )
                );

                throw new IllegalStateException("Invite subject mismatch for local user");
            }

            user.bindExternalSubjectId(keycloakUserId);

            // Tamper-evident audit for subject binding
            adminAuditEventService.record(
                    AdminAuditActionType.INVITE_SUBJECT_BOUND,
                    tenant.getId(),
                    actor.getExternalSubjectId(),
                    user.getId(),
                    new InviteSubjectBindingAuditMetadata(
                            user.getId(),
                            keycloakUserId,
                            sha256Hex(normalizedEmail)
                    )
            );

            String inviteLink =
                    frontendBaseUrl + "/invite?token=" + invite.getToken();

            mailService.sendInvite(
                    tenant,
                    normalizedEmail,
                    firstName,
                    lastName,
                    jobTitle,
                    department,
                    inviteLink,
                    temporaryPassword
            );

            success = true;
        } catch (RuntimeException ex) {
            identityAnomalyMetrics.incrementBindFailure(tenant.getId().toString());

            adminAuditEventService.record(
                    AdminAuditActionType.INVITE_SUBJECT_BIND_FAILED,
                    tenant.getId(),
                    actor.getExternalSubjectId(),
                    null,
                    new InviteSubjectBindingAuditMetadata(
                            null,
                            null,
                            sha256Hex(normalizedEmail)
                    )
            );

            // Best-effort compensation: if Keycloak user was created but local txn fails,
            // attempt deletion to avoid orphaned Keycloak accounts.
            if (keycloakUserId != null && !keycloakUserId.isBlank()) {
                try {
                    keycloakAdminClient.deleteUserById(keycloakUserId);
                } catch (Exception cleanupEx) {
                    log.warn("invite_create_keycloak_compensation_failed subjectId={}", keycloakUserId, cleanupEx);
                }
            }
            throw ex;
        } finally {
            AdminAuditActionType actionType =
                    success
                            ? AdminAuditActionType.USER_INVITED
                            : AdminAuditActionType.INVITE_FAILED;

            adminAuditEventService.record(
                    actionType,
                    tenant.getId(),
                    actor.getExternalSubjectId(),
                    null,
                    new InviteAuditMetadata(
                            normalizedEmail,
                            invite != null ? invite.getId().toString() : null
                    )
            );
        }
    }

    @Transactional
    public void consumeInviteIfPresent(HttpSession session) {
        if (session == null) return;

        Object raw = session.getAttribute(InviteSessionKeys.INVITE_TOKEN);
        if (!(raw instanceof String token) || token.isBlank()) return;

        Invite invite = validateInviteOrThrow(token);
        acceptInviteOrThrow(invite);

        session.removeAttribute(InviteSessionKeys.INVITE_TOKEN);
    }

    @Transactional
    public void validateAndStoreInviteToken(String token, HttpSession session) {
        if (token == null || token.isBlank()) {
            throw new InviteNotFoundException("Invite token not provided");
        }

        validateInviteOrThrow(token);
        session.setAttribute(InviteSessionKeys.INVITE_TOKEN, token);
    }

    @Transactional
    Invite validateInviteOrThrow(String token) {
        Invite invite = inviteRepository.findByTokenForUpdate(token)
                .orElseThrow(() ->
                        new InviteNotFoundException("Invite not found for the provided token")
                );

        UUID inviteTenantId = requireInviteTenant(invite);

        if (invite.isExpired()) {

            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_EXPIRED",
                    null
            );

            throw new InviteNotFoundException("Invite link invalid or expired");
        }

        if (invite.getStatus() == InviteStatus.ACCEPTED) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_REPLAY",
                    null
            );
            throw new InviteNotFoundException("Invite link invalid or expired");
        }

        return invite;
    }

    @Transactional
    void acceptInviteOrThrow(Invite invite) {
        UUID inviteTenantId = requireInviteTenant(invite);
        var currentUser = userService.getRequiredCurrentUser();

        if (invite.getStatus() == InviteStatus.ACCEPTED) {

            onboardingAuditService.recordFailure(
                    currentUser.getId(),
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_REPLAY",
                    null
            );

            throw new IllegalArgumentException("Invite link invalid or expired");
        }

        onboardingAuditService.recordSuccess(
                currentUser.getId(),
                currentUser.getExternalSubjectId(),
                inviteTenantId,
                invite.getId(),
                null
        );

        invite.markAccepted();
        inviteRepository.save(invite);
    }

    /**
     * Tenant-scoped revoke with hard boundary enforcement.
     * Intended for /api/tenants/{tenantId}/invites/{inviteId}/revoke.
     */
    @Transactional
    public void revokeInviteInTenant(UUID inviteId, UUID tenantId) {
        Objects.requireNonNull(inviteId, "inviteId");
        Objects.requireNonNull(tenantId, "tenantId");

        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() ->
                        new InviteNotFoundException("Invite not found")
                );

        UUID inviteTenantId = requireInviteTenant(invite);

        if (!inviteTenantId.equals(tenantId)) {
            throw new IllegalArgumentException("Invite does not belong to tenant");
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new IllegalStateException("Only PENDING invites can be revoked");
        }

        User user = invite.getUser();

        if (user != null && user.getStatus() == UserStatus.ACTIVE) {
            throw new IllegalStateException("Cannot revoke invite for ACTIVE user");
        }

        if (user != null && user.getExternalSubjectId() != null) {
            try {
                keycloakAdminClient.deleteUserById(user.getExternalSubjectId());
            } catch (Exception ex) {
                var actor = userService.getRequiredCurrentUser();
                String correlationId = org.slf4j.MDC.get(
                        RequestCorrelationIdFilter.MDC_KEY
                );

                log.warn("application_error",
                        kv("schema_version", "docflow_siem_v1"),
                        kv("event.category", "application"),
                        kv("event.action", "invite_revoke_keycloak_cleanup_failed"),
                        kv("event.outcome", "failure"),
                        kv("correlation.id", correlationId),
                        kv("tenant.id", inviteTenantId),
                        kv("actor.subject_id", actor.getExternalSubjectId()),
                        kv("error.code", "KEYCLOAK_DELETE_FAILED"),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );
            }
        }

        // Edge case: invite revoked while the user still has an active session.
        // Expire sessions for the subject so subsequent requests fail fast.
        if (user != null && user.getExternalSubjectId() != null) {
            expireSessionsForSubject(user.getExternalSubjectId());
        }

        inviteRepository.delete(invite);

        var actor = userService.getRequiredCurrentUser();

        adminAuditEventService.record(
                AdminAuditActionType.INVITE_REVOKED,
                inviteTenantId,
                actor.getExternalSubjectId(),
                null,
                new InviteAuditMetadata(
                        invite.getEmail(),
                        invite.getId().toString()
                )
        );
    }

    @Transactional
    public CleanupResult cleanupExpiredInvitesAndOrphanedUsers(UUID targetTenantId) {
        Instant now = Instant.now();
        var actor = userService.getRequiredCurrentUser();

        try {

            List<Invite> expiredInvites =
                    inviteRepository.findByTenantIdAndStatusAndExpiresAtBefore(
                            targetTenantId,
                            InviteStatus.PENDING,
                            now
                    );

            List<UUID> orphanUserIds = expiredInvites.stream()
                    .map(Invite::getUser)
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .toList();

            int deletedUsers =
                    userService.deleteUnactivatedInvitedUsers(orphanUserIds);

            inviteRepository.deleteAll(expiredInvites);

            adminAuditEventService.record(
                    AdminAuditActionType.INVITE_CLEANUP,
                    targetTenantId,
                    actor.getExternalSubjectId(),
                    null,
                    new InviteCleanupAuditMetadata(
                            expiredInvites.size(),
                            deletedUsers
                    )
            );

            return new CleanupResult(
                    expiredInvites.size(),
                    deletedUsers
            );
        } catch (RuntimeException ex) {
            adminAuditEventService.record(
                    AdminAuditActionType.INVITE_PURGE_FAILED,
                    targetTenantId,
                    actor.getExternalSubjectId(),
                    null,
                    new InviteCleanupAuditMetadata(
                            0,
                            0
                    )
            );
            throw ex;
        }
    }

    private static UUID requireInviteTenant(Invite invite) {
        UUID tenantId = invite.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Invite is missing tenantId");
        }
        return tenantId;
    }

    private static String generateTemporaryPassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private void expireSessionsForSubject(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) return;

        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal == null) continue;

            String principalSubject = null;
            if (principal instanceof OidcUser oidc) {
                principalSubject = oidc.getSubject();
            }

            if (!subjectId.equals(principalSubject)) {
                continue;
            }

            List<SessionInformation> sessions =
                    sessionRegistry.getAllSessions(principal, false);

            for (SessionInformation s : sessions) {
                if (s != null) {
                    s.expireNow();
                }
            }
        }
    }
}