package com.brutecx.docflow_backend.security.audit.identity;

import com.brutecx.docflow_backend.security.audit.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.security.audit.keycloak.KeycloakUser;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

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
        if (subjectId == null || "UNKNOWN".equals(subjectId)) {
            return;
        }

        Optional<UserIdentityProjection> existingOpt = repo.findById(subjectId);
        if (existingOpt.isPresent()) {
            UserIdentityProjection existing = existingOpt.get();
            if (existing.isInitialized() && existing.isFresh(Duration.ofHours(24))) {
                return;
            }
        }

        UserIdentityProjection projection = existingOpt
                .orElseGet(() -> new UserIdentityProjection(subjectId, "KEYCLOAK"));

        KeycloakUser kcUser = keycloak.fetchUser(subjectId);
        if (kcUser == null) {
            return;
        }

        projection.update(
                kcUser.username(),
                kcUser.email(),
                kcUser.displayName()
        );

        repo.save(projection);
    }
}
