package com.brutecx.docflow_backend.security.audit.identity;

import com.brutecx.docflow_backend.security.audit.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.security.audit.keycloak.KeycloakUser;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Service to enrich and ensure the projection of user identity information
 * from Keycloak into the local UserIdentityProjection repository.
 * Handles asynchronous updates and checks for data freshness.
 */
@Slf4j
@Service
public class UserIdentityEnrichmentService implements IUserIdentityProjectionService {

    private final UserIdentityProjectionRepository repo;
    private final KeycloakAdminClient keycloak;

    public UserIdentityEnrichmentService(
            UserIdentityProjectionRepository repo,
            KeycloakAdminClient keycloak
    ) {
        this.repo = repo;
        this.keycloak = keycloak;
    }

    @Async
    @Transactional
    public void ensureProjected(String subjectId) {

        log.debug("Ensuring projected identity for user {}", subjectId);

        if (subjectId == null || "UNKNOWN".equals(subjectId)) {
            return;
        }

        log.info("Ensuring projected identity for user {}", subjectId);

        Optional<UserIdentityProjection> existingOpt = repo.findById(subjectId);
        if (existingOpt.isPresent()) {
            UserIdentityProjection existing = existingOpt.get();
            log.info("Found existing identity for user {}", subjectId);

            if (existing.isInitialized() && existing.isFresh(Duration.ofHours(24))) {
                log.info("Found existing identity for user {}", subjectId);

                return;
            }
        }

        log.info("Found existing identity for user {}", subjectId);

        UserIdentityProjection projection = existingOpt
                .orElseGet(() -> new UserIdentityProjection(subjectId, "KEYCLOAK"));

        log.info("Found existing identity for user {}", subjectId);
        KeycloakUser kcUser = keycloak.fetchUser(subjectId);
        if (kcUser == null) {
            log.warn("Keycloak user not found for subjectId {}", subjectId);
            return;
        }

        log.info("Found existing identity for user {}", subjectId);
        projection.update(
                kcUser.username(),
                kcUser.email(),
                kcUser.displayName()
        );

        log.info("Updated existing identity for user {}", subjectId);
        repo.save(projection);
    }
}
