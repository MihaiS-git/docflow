package com.brutecx.docflow_backend.security.auth;

import com.brutecx.docflow_backend.application.invite.InviteApplicationService;
import com.brutecx.docflow_backend.domain.tenant.Tenant;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/**
 * Listener for successful authentication events.
 * Handles both interactive and non-interactive authentication success events.
 * On successful authentication, it ensures the user exists in the database,
 * updates their last login information, and associates them with the current tenant.
 * Works specifically with OIDC users (e.g., from Keycloak).
 */
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener {

    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final InviteApplicationService inviteApplicationService;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        handleAuthentication(event.getAuthentication());

        // consume invite token if present (stored in HttpSession by invite landing page)
        if (event.getAuthentication() instanceof OAuth2AuthenticationToken oauth) {
            Object principal = oauth.getPrincipal();
            if (principal instanceof OidcUser oidcUser) {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                HttpSession session = (attrs != null ? attrs.getRequest().getSession(false) : null);
                inviteApplicationService.consumeInviteIfPresent(session, oidcUser);
            }
        }
    }

    @Transactional
    public void handleAuthentication(Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return; // not a Keycloak/OIDC principal → ignore
        }

        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String firstName = oidcUser.getGivenName();
        String lastName = oidcUser.getFamilyName();

        Tenant tenant = tenantService.getCurrentTenant();

        User user = userRepository.findByExternalSubjectId(subject)
                .orElseGet(() -> createNewUser(
                        tenant,
                        subject,
                        email,
                        firstName,
                        lastName
                ));

        updateLastLogin(user);
        userRepository.save(user);
    }

    private User createNewUser(
            Tenant tenant,
            String subject,
            String email,
            String firstName,
            String lastName
    ) {
        User user = new User(
                subject,
                email,
                firstName != null ? firstName : "",
                lastName != null ? lastName : ""
        );

        tenant.addUser(user); // enforces tenant invariant
        return user;
    }

    private void updateLastLogin(User user) {
        user.setLastLoginAt(Instant.now());

        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            user.setLastLoginIp(req.getRemoteAddr());
            user.setLastLoginUserAgent(req.getHeader("User-Agent"));
        }
    }

}
