package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.admin.*;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditService;
import com.brutecx.docflow_backend.domain.invite.Invite;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.IUserProvisioningService;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
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
    private final AuditRequestContextExtractor auditContextExtractor;
    private final IAdminAuditEventService adminAuditEventService;
    private final OnboardingAuditService onboardingAuditService;
    private final UserService userService;

    /**
     * Phase 1 — Admin creates invite
     */
    @Transactional
    public void createAndSendInvite(
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String department
    ) {
        Tenant tenant = tenantService.getCurrentTenant();
        String normalizedEmail = email.toLowerCase(java.util.Locale.ROOT);

        var ctx = auditContextExtractor.fromCurrentRequest();
        UUID actorUserId = userService.getRequiredCurrentUser().getId();
        String subjectId = userService.getRequiredCurrentUser().getExternalSubjectId();

        boolean success = false;
        RuntimeException failure = null;
        Invite invite = null;

        try {
            userProvisioningService.provisionInvitedUser(
                    tenant,
                    normalizedEmail,
                    firstName,
                    lastName,
                    jobTitle,
                    department
            );

            invite = Invite.create(normalizedEmail);
            inviteRepository.save(invite);

            String temporaryPassword = generateTemporaryPassword();

            keycloakAdminClient.ensureInviteUserExistsWithRequiredActionsAndTempPassword(
                    normalizedEmail,
                    temporaryPassword
            );

            String inviteLink = frontendBaseUrl + "/invite?token=" + invite.getToken();

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
            AdminAuditMetadata metadata =
                    new InviteAuditMetadata(
                            normalizedEmail,
                            invite != null ? invite.getId().toString() : null,
                            success ? InviteOutcome.SUCCESS : InviteOutcome.FAILURE,
                            success ? null : failure.getClass().getSimpleName()
                    );

            adminAuditEventService.record(
                    actorUserId,
                    ctx.ip(),
                    ctx.userAgent(),
                    ctx.requestId(),
                    subjectId,
                    tenant.getId(),
                    AdminAuditActionType.USER_INVITED,
                    null,
                    metadata
            );
        }
    }

    /**
     * Phase 4 — Post-login consumption
     */
    @Transactional
    public void consumeInviteIfPresent(HttpSession session) {
        if (session == null) return;

        Object raw = session.getAttribute(InviteSessionKeys.INVITE_TOKEN);
        if (!(raw instanceof String token) || token.isBlank()) return;

        Invite invite = validateInviteOrThrow(token);
        acceptInviteOrThrow(invite);

        session.removeAttribute(InviteSessionKeys.INVITE_TOKEN);
    }

    /**
     * Phase 2 — Invite validation
     */
    @Transactional(readOnly = true)
    public void validateAndStoreInviteToken(String token, HttpSession session) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invite link invalid or expired");
        }

        Invite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invite link invalid or expired"));

        if (isInvalid(invite)) {
            throw new IllegalArgumentException("Invite link invalid or expired");
        }

        session.setAttribute(InviteSessionKeys.INVITE_TOKEN, token);
    }

    private static boolean isInvalid(Invite invite) {
        return invite.isExpired()
                || invite.getStatus() == InviteStatus.ACCEPTED
                || invite.getStatus() == InviteStatus.REVOKED
                || invite.getStatus() == InviteStatus.EXPIRED;
    }

    @Transactional(readOnly = true)
    Invite validateInviteOrThrow(String token) {
        Invite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invite link invalid or expired"));

        if (isInvalid(invite)) {
            throw new IllegalArgumentException("Invite link invalid or expired");
        }

        return invite;
    }

    @Transactional
    void acceptInviteOrThrow(Invite invite) {
        if (invite.getStatus() == InviteStatus.ACCEPTED) {
            throw new IllegalArgumentException("Invite link invalid or expired");
        }
        UUID actorUserId = userService.getRequiredCurrentUser().getId();
        String subjectId = userService.getRequiredCurrentUser().getExternalSubjectId();

        // ONBOARDING AUDIT — exactly once
        onboardingAuditService.recordOnce(
                actorUserId,
                subjectId,
                tenantService.getCurrentTenant().getId(),
                invite.getId()
        );

        invite.markAccepted();
        inviteRepository.save(invite);
    }

    // strong temporary password generator
    private static String generateTemporaryPassword() {
        byte[] bytes = new byte[32]; // 256-bit entropy
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

