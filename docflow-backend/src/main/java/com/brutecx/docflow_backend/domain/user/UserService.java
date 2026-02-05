package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public void setSubjectId(Tenant tenant, String normalizedEmail, String keycloakUserId) {
        userRepository.findByTenantIdAndEmailIgnoreCase(tenant.getId(), normalizedEmail)
                .ifPresentOrElse(user -> {
                    user.bindExternalSubjectId(keycloakUserId);
                    userRepository.save(user);
                }, () -> {
                    log.error(
                            "INVITE: Local user row missing for tenantId={} email={} after Keycloak provisioning userId={}",
                            tenant.getId(),
                            normalizedEmail,
                            keycloakUserId
                    );
                });
    }

    public CurrentUserResult resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }

        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new IllegalStateException("Authenticated principal is not OIDC");
        }

        String subject = oidcUser.getSubject();

        return userRepository.findByExternalSubjectId(subject)
                .map(user -> {
                    return switch (user.getStatus()) {
                        case ACTIVE -> new CurrentUserResult(CurrentUserState.ACTIVE, user);
                        case LOCKED -> new CurrentUserResult(CurrentUserState.LOCKED, null);
                        case DISABLED -> new CurrentUserResult(CurrentUserState.DISABLED, null);
                    };
                })
                .orElseGet(() ->
                        new CurrentUserResult(CurrentUserState.BOOTSTRAP, null)
                );
    }

    /**
     * Deletes invited users that were never activated.
     * <p>
     * Safety guarantees:
     * - only LOCKED users
     * - only users without externalSubjectId
     * - caller controls which user IDs are eligible
     */
    @Transactional
    public int deleteUnactivatedInvitedUsers(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }

        List<User> users =
                userRepository.findByStatusAndIdIn(
                        UserStatus.LOCKED,
                        userIds
                );

        int deleted = 0;

        for (User user : users) {
            // HARD GUARD: never delete users already bound to IdP
            if (user.getExternalSubjectId() != null) {
                continue;
            }

            userRepository.delete(user);
            deleted++;
        }

        return deleted;
    }

    @Transactional
    public void deleteUser(User user) {
        userRepository.delete(user);
    }

}
