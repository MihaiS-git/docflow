package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.api.error.InviteNotFoundException;
import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.audit.admin.*;
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
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

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

    /* =========================================================
       CREATE + SEND
       ========================================================= */

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
        RuntimeException failure = null;
        Invite invite = null;

        try {

            User user = userProvisioningService.provisionInvitedUser(
                    tenant,
                    normalizedEmail,
                    firstName,
                    lastName,
                    jobTitle,
                    department
            );

            // Apply requested tenant role (default MEMBER).
            TenantRole effectiveRole = (tenantRole != null) ? tenantRole : TenantRole.MEMBER;
            membershipRepository.findByUserIdAndTenantId(user.getId(), tenant.getId())
                    .ifPresent(m -> m.changeRole(effectiveRole));

            invite = Invite.create(normalizedEmail, tenant.getId(), effectiveRole);
            invite.linkUser(user);
            inviteRepository.save(invite);

            String temporaryPassword = generateTemporaryPassword();

            String keycloakUserId =
                    keycloakAdminClient.ensureInviteUserExistsWithRequiredActionsAndTempPassword(
                            normalizedEmail,
                            temporaryPassword
                    );

            userService.setSubjectId(normalizedEmail, keycloakUserId);

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
            failure = ex;
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

    /* =========================================================
       CONSUME
       ========================================================= */

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

    /* =========================================================
       VALIDATION
       ========================================================= */

    @Transactional
    Invite validateInviteOrThrow(String token) {

        Invite invite = inviteRepository.findByToken(token)
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

    /* =========================================================
       ACCEPT
       ========================================================= */

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

    /* =========================================================
       REVOKE
       ========================================================= */

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
                log.warn(
                        "INVITE_REVOKE: failed to delete Keycloak user subjectId={}",
                        user.getExternalSubjectId(),
                        ex
                );
            }
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

    /* =========================================================
       CLEANUP
       ========================================================= */

    @Transactional
    public CleanupResult cleanupExpiredInvitesAndOrphanedUsers(UUID targetTenantId) {

        Instant now = Instant.now();

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

        var actor = userService.getRequiredCurrentUser();

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
    }

    /* =========================================================
       UTIL
       ========================================================= */

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
}
