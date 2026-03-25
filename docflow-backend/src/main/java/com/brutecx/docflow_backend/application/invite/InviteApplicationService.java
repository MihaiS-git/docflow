package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.api.error.*;
import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.InviteAuditMetadata;
import com.brutecx.docflow_backend.audit.admin.InviteBatchExpireMetadata;
import com.brutecx.docflow_backend.audit.metrics.IdentityAnomalyMetrics;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditService;
import com.brutecx.docflow_backend.domain.invite.Invite;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import com.brutecx.docflow_backend.domain.tenant.*;
import com.brutecx.docflow_backend.domain.user.IUserProvisioningService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.brutecx.docflow_backend.domain.user.UserStatus;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static com.brutecx.docflow_backend.infrastructure.security.HashUtils.sha256Hex;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteApplicationService {

    @Value("${docflow.security.frontend-base-url}")
    private String frontendBaseUrl;

    private final InviteRepository inviteRepository;
    private final IMailService mailService;
    private final TenantService tenantService;
    private final IUserProvisioningService userProvisioningService;
    private final IAdminAuditEventService adminAuditEventService;
    private final OnboardingAuditService onboardingAuditService;
    private final UserService userService;
    private final UserTenantMembershipRepository membershipRepository;
    private final TenantMembershipService tenantMembershipService;
    private final IdentityAnomalyMetrics identityAnomalyMetrics;
    private final KeycloakAdminClient keycloakAdminClient;

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

        tenantService.requireActiveTenant(targetTenantId);
        Tenant tenant = tenantService.getRequired(targetTenantId);

        String normalizedEmail = normalizeEmail(email);
        var actor = userService.getRequiredCurrentUser();

        if (membershipRepository.existsActiveMembershipByEmailAndTenantId(
                normalizedEmail,
                targetTenantId,
                MembershipStatus.ACTIVE
        )) {
            throw new UserAlreadyTenantMemberException(
                    "User is already a member of this tenant."
            );
        }

        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        jobTitle = jobTitle == null ? null : jobTitle.trim();
        department = department == null ? null : department.trim();

        Invite invite;
        String rawToken;

        try {
            TenantRole effectiveRole =
                    (tenantRole != null) ? tenantRole : TenantRole.MEMBER;

            var existing = inviteRepository
                    .findByTenantIdAndEmail(
                            targetTenantId,
                            normalizedEmail
                    )
                    .orElse(null);

            if (existing != null) {
                if (existing.isPending() || existing.isActivated()) {
                    throw new DuplicateInviteException(
                            "A pending invite already exists for this email in this tenant."
                    );
                }

                if (existing.isAccepted()) {
                    throw new UserAlreadyTenantMemberException(
                            "User already accepted invite."
                    );
                }

                rawToken = existing.resetForResend(
                        firstName,
                        lastName,
                        jobTitle,
                        department,
                        effectiveRole
                );

                invite = existing;
            } else {
                var created = Invite.create(
                        normalizedEmail,
                        firstName,
                        lastName,
                        jobTitle,
                        department,
                        tenant.getId(),
                        effectiveRole
                );

                invite = created.invite();
                rawToken = created.rawToken();
            }

            inviteRepository.save(invite);

            String inviteLink = frontendBaseUrl + "/invite?token=" + rawToken;

            mailService.sendInvite(
                    tenant,
                    normalizedEmail,
                    firstName,
                    lastName,
                    jobTitle,
                    department,
                    inviteLink
            );

            adminAuditEventService.record(
                    AdminAuditActionType.USER_INVITED,
                    tenant.getId(),
                    actor.getExternalSubjectId(),
                    null,
                    new InviteAuditMetadata(
                            normalizedEmail,
                            invite.getId().toString()
                    )
            );
        } catch (RuntimeException ex) {
            adminAuditEventService.record(
                    AdminAuditActionType.INVITE_FAILED,
                    tenant.getId(),
                    actor.getExternalSubjectId(),
                    null,
                    new InviteAuditMetadata(
                            normalizedEmail,
                            null
                    )
            );
            throw ex;
        }
    }

    @Transactional
    public void validateAndStoreInviteToken(String token, HttpSession session) {
        if (token == null || token.isBlank()) {
            throw new InviteNotFoundException("Invite token not provided");
        }

        if (session == null) {
            throw new IllegalStateException("HTTP session is required for invite login flow");
        }

        Invite invite = inviteRepository.findByTokenForUpdate(sha256Hex(token))
                .orElseThrow(() ->
                        new InviteNotFoundException("Invite not found for the provided token")
                );

        activateInviteOrThrow(invite);
        session.setAttribute(InviteSessionKeys.INVITE_TOKEN, token);
    }

    @Transactional
    public void consumeInviteIfPresent(HttpSession session) {
        if (session == null) {
            return;
        }

        Object raw = session.getAttribute(InviteSessionKeys.INVITE_TOKEN);
        if (!(raw instanceof String token) || token.isBlank()) {
            return;
        }

        Invite invite = inviteRepository.findByTokenForUpdate(sha256Hex(token))
                .orElseThrow(() ->
                        new InviteNotFoundException("Invite not found for the provided token")
                );

        validateInviteStateOrThrow(invite);
        acceptInviteOrThrow(invite);

        session.removeAttribute(InviteSessionKeys.INVITE_TOKEN);
    }

    @Transactional
    void acceptInviteOrThrow(Invite invite) {
        UUID inviteTenantId = requireInviteTenant(invite);
        OidcUser oidcUser = requireAuthenticatedOidcUser();
        String authenticatedEmail = normalizeEmail(oidcUser.getEmail());
        String invitedEmail = normalizeEmail(invite.getEmail());

        if (invite.getStatus() == InviteStatus.ACCEPTED) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_REPLAY"
            );

            throw new IllegalArgumentException("Invite link invalid or expired");
        }

        if (!invitedEmail.equals(authenticatedEmail)) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_EMAIL_MISMATCH"
            );

            throw new IllegalArgumentException("Authenticated user does not match invited email");
        }

        User currentUser = userProvisioningService.provisionInvitedUser(
                oidcUser.getSubject(),
                authenticatedEmail,
                invite.getFirstName(),
                invite.getLastName(),
                invite.getJobTitle(),
                invite.getDepartment()
        );

        TenantRole targetRole = invite.getTenantRole() != null
                ? invite.getTenantRole()
                : TenantRole.MEMBER;

        tenantMembershipService.ensureMembership(
                currentUser.getId(),
                inviteTenantId,
                targetRole
        );

        if (currentUser.getExternalSubjectId() == null
                || !currentUser.getExternalSubjectId().equals(oidcUser.getSubject())) {
            identityAnomalyMetrics.incrementBindFailure(inviteTenantId.toString());
        }

        onboardingAuditService.recordSuccess(
                currentUser.getId(),
                currentUser.getExternalSubjectId(),
                inviteTenantId,
                invite.getId()
        );

        invite.markAccepted();

        try {
            inviteRepository.save(invite);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateInviteException("Invite not persisted.");
        }
    }

    @Transactional
    public void revokeInviteInTenant(UUID inviteId, UUID tenantId) {
        Objects.requireNonNull(inviteId, "inviteId");
        Objects.requireNonNull(tenantId, "tenantId");

        tenantService.requireActiveTenant(tenantId);

        Invite invite = inviteRepository.findByIdForUpdate(inviteId)
                .orElseThrow(() ->
                        new InviteNotFoundException("Invite not found")
                );

        UUID inviteTenantId = requireInviteTenant(invite);

        if (!inviteTenantId.equals(tenantId)) {
            throw new IllegalArgumentException("Invite does not belong to tenant");
        }

        if (invite.isTerminal()) {
            throw new IllegalStateException("Cannot revoke terminal invite");
        }

        invite.markRevoked();
        inviteRepository.save(invite);

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
    public ExpireInvitesResult expirePendingInvites(UUID targetTenantId) {
        Instant now = Instant.now();
        var actor = userService.getRequiredCurrentUser();

        try {
            List<Invite> expiredInvites =
                    inviteRepository.findByTenantIdAndStatusAndExpiresAtBefore(
                            targetTenantId,
                            InviteStatus.PENDING,
                            now
                    );

            expiredInvites.forEach(Invite::markExpired);
            inviteRepository.saveAll(expiredInvites);

            adminAuditEventService.record(
                    AdminAuditActionType.INVITE_EXPIRED_BATCH,
                    targetTenantId,
                    actor.getExternalSubjectId(),
                    null,
                    new InviteBatchExpireMetadata(expiredInvites.size())
            );

            return new ExpireInvitesResult(expiredInvites.size());
        } catch (RuntimeException ex) {
            adminAuditEventService.record(
                    AdminAuditActionType.INVITE_EXPIRE_FAILED,
                    targetTenantId,
                    actor.getExternalSubjectId(),
                    null,
                    new InviteBatchExpireMetadata(0)
            );
            throw ex;
        }
    }

    private void validateInviteStateOrThrow(Invite invite) {
        UUID inviteTenantId = requireInviteTenant(invite);

        if (invite.isExpired()) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_EXPIRED"
            );

            throw new InviteNotFoundException("Invite link invalid or expired");
        }

        if (invite.getStatus() == InviteStatus.REVOKED) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_REVOKED"
            );

            throw new InviteNotFoundException("Invite link invalid or expired");
        }

        if (invite.getStatus() == InviteStatus.ACCEPTED) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_REPLAY"
            );

            throw new InviteNotFoundException("Invite link invalid or expired");
        }
    }

    private void activateInviteOrThrow(Invite invite) {
        UUID inviteTenantId = requireInviteTenant(invite);

        if (invite.isExpired()) {
            if (!invite.isTerminal()) {
                invite.markExpired();
                inviteRepository.save(invite);
            }

            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_EXPIRED"
            );

            throw new InviteNotFoundException("Invite link invalid or expired");
        }

        if (invite.getStatus() == InviteStatus.REVOKED) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_REVOKED"
            );

            throw new InviteNotFoundException("Invite link invalid or expired");
        }

        if (invite.getStatus() == InviteStatus.ACCEPTED) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_REPLAY"
            );

            throw new InviteNotFoundException("Invite link invalid or expired");
        }

        if (invite.isActivated()) {
            return;
        }

        if (!invite.isPending()) {
            throw new IllegalStateException("Only PENDING invites can be activated");
        }

        try {
            String normalizedEmail = normalizeEmail(invite.getEmail());
            User existingUser = userService.findByEmailIgnoreCase(normalizedEmail)
                    .orElse(null);

            log.info(
                    "Invite activation decision email={} localUser={} subjectPresent={} status={}",
                    normalizedEmail,
                    existingUser != null,
                    existingUser != null && existingUser.getExternalSubjectId() != null,
                    existingUser != null ? existingUser.getStatus() : null
            );

            if (existingUser != null
                    && existingUser.getExternalSubjectId() != null
                    && !existingUser.getExternalSubjectId().isBlank()
                    && existingUser.getStatus() == UserStatus.ACTIVE) {
                log.info(
                        "Invite activation reused local user id={} subject={} email={}",
                        existingUser.getId(),
                        existingUser.getExternalSubjectId(),
                        normalizedEmail
                );
            } else {
                KeycloakAdminClient.EnsureUserResult ensured =
                        keycloakAdminClient.ensureUserExistsByEmail(normalizedEmail);

                if (ensured.created()) {
                    keycloakAdminClient.sendPasswordSetupEmail(ensured.userId());
                }
            }

            invite.markActivated();
            inviteRepository.save(invite);

        } catch (RuntimeException ex) {
            onboardingAuditService.recordFailure(
                    null,
                    inviteTenantId,
                    invite.getId(),
                    "INVITE_ACTIVATION_FAILED"
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

    private static OidcUser requireAuthenticatedOidcUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new IllegalStateException("Authenticated OIDC user is required");
        }

        return oidcUser;
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        return normalized;
    }
}