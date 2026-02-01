package com.brutecx.docflow_backend.application.invite;

import com.brutecx.docflow_backend.application.mail.IMailService;
import com.brutecx.docflow_backend.domain.invite.Invite;
import com.brutecx.docflow_backend.domain.invite.InviteRepository;
import com.brutecx.docflow_backend.domain.invite.InviteStatus;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class InviteApplicationService {

    @Value("${KC_HOSTNAME}")
    private String kcHostname;

    private final InviteRepository inviteRepository;
    private final IMailService mailService;
    private final KeycloakAdminClient keycloakAdminClient;

    public void createAndSendInvite(String email) {
        log.info("INVITE: start provisioning email={}", email);
        Invite invite = Invite.create(email);
        inviteRepository.save(invite);

        keycloakAdminClient.ensureInviteUserExistsWithRequiredActions(email);

        String inviteLink =
                kcHostname + "/api/invites/accept?token=" + invite.getToken();

        mailService.sendInvite(email, inviteLink);
    }

    @Transactional
    public void consumeInviteIfPresent(HttpSession session, OidcUser oidcUser) {
        if (session == null) return;

        Object raw = session.getAttribute(InviteSessionKeys.INVITE_TOKEN);
        if (!(raw instanceof String token) || token.isBlank()) return;

        Invite invite = validateInviteOrThrow(token);
        acceptInviteOrThrow(invite);

        session.removeAttribute(InviteSessionKeys.INVITE_TOKEN);
    }

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

}

