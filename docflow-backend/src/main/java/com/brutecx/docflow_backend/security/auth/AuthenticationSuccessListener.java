package com.brutecx.docflow_backend.security.auth;

import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;

/**
 * Listener for successful authentication events.
 * Handles both interactive and non-interactive authentication success events.
 * On successful authentication, it ensures the user exists in the database,
 * updates their last login information, and associates them with the current tenant.
 * Works specifically with OIDC users (e.g., from Keycloak).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessListener {

    private final UserRepository userRepository;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return; // not a Keycloak/OIDC principal → ignore
        }

        String subject = oidcUser.getSubject();
        Optional<User> existing = userRepository.findByExternalSubjectId(subject);

        User user;
        if (existing.isEmpty()) {
            if (!userRepository.existsByExternalSubjectIdIsNotNull()) {
                userRepository.findByEmail(oidcUser.getEmail().toLowerCase())
                        .ifPresent(u -> {
                            u.bindExternalSubjectId(subject);
                            u.activate(); // status = ACTIVE
                            updateLastLogin(u);
                            u = userRepository.save(u);

                            log.warn("BOOTSTRAP: Activated first admin user {}", u.getEmail());
                        });
            }
        } else {
            user = existing.get();
            updateLastLogin(user);
            userRepository.save(user);
        }
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
