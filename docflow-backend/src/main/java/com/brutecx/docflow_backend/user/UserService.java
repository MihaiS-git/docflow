package com.brutecx.docflow_backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getRequiredCurrentUser() {
        log.info("Getting current user");

        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Current user is not authenticated");
            throw new IllegalStateException("No authenticated user in security context");
        }

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            log.error("Authentication principal is not OidcUser: {}", authentication.getPrincipal());
            throw new IllegalStateException("Authenticated principal is not an OIDC user");
        }

        String externalSubjectId = oidcUser.getSubject(); // CHANGED: correct OIDC subject
        log.info("External subject id (OIDC sub): {}", externalSubjectId);

        return userRepository.findByExternalSubjectId(externalSubjectId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated subject not mapped to local user: " + externalSubjectId
                        )
                );
    }

    public User getRequired(UUID userId) {
        return userRepository.getRequired(userId);
    }
}
