package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.domain.invite.Invite;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.IUserProvisioningService;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;


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
        log.info("INVITE: provisioning user + invite for {}", email);

        Tenant tenant = tenantService.getCurrentTenant();
        String normalizedEmail = email.toLowerCase(java.util.Locale.ROOT);
        userProvisioningService.provisionInvitedUser(
                tenant,
                normalizedEmail,
                firstName,
                lastName,
                jobTitle,
                department
        );

        // 1. Create invite (domain)
        Invite invite = Invite.create(normalizedEmail);
        inviteRepository.save(invite);

        // ADDED: generate strong temporary password (single-use)
        String temporaryPassword = generateTemporaryPassword();

        // CHANGED: ensure user + required actions + set temporary password
        keycloakAdminClient.ensureInviteUserExistsWithRequiredActionsAndTempPassword(
                normalizedEmail,
                temporaryPassword
        );

        // 3. Build FRONTEND invite link (token is frontend-owned)
        String inviteLink = frontendBaseUrl + "/invite?token=" + invite.getToken();

        // send invite email INCLUDING temporary password
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

