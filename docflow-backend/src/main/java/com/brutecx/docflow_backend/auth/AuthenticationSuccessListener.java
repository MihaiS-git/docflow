package com.brutecx.docflow_backend.auth;

import com.brutecx.docflow_backend.tenant.Tenant;
import com.brutecx.docflow_backend.tenant.TenantService;
import com.brutecx.docflow_backend.user.User;
import com.brutecx.docflow_backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener {

    private final UserRepository userRepository;
    private final TenantService tenantService;

    @EventListener
    @Transactional
    public void onInteractiveSuccess(InteractiveAuthenticationSuccessEvent event) {
        handleAuthentication(event.getAuthentication());
    }

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        handleAuthentication(event.getAuthentication());
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
