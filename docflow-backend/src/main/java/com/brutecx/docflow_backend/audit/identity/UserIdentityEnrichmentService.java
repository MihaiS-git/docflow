package com.brutecx.docflow_backend.audit.identity;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final IdentityProjectionAuditEventRepository auditRepo;

    public UserIdentityEnrichmentService(
            UserIdentityProjectionRepository repo,
            KeycloakAdminClient keycloak,
            IdentityProjectionAuditEventRepository auditRepo
    ) {
        this.repo = repo;
        this.keycloak = keycloak;
        this.auditRepo = auditRepo;
    }

    @Async
    @Transactional
    public void ensureProjected(String subjectId) {
        if (subjectId == null || "UNKNOWN".equals(subjectId)) {
            return;
        }

        try {
            Optional<UserIdentityProjection> existingOpt = repo.findById(subjectId);

            if (existingOpt.isPresent()) {
                UserIdentityProjection existing = existingOpt.get();

                if (existing.isInitialized() && existing.isFresh(Duration.ofHours(24))) {
                    log.debug("Identity projection fresh for subjectId={}", subjectId);
                    return;
                }
            }

            UserIdentityProjection projection = existingOpt
                    .orElseGet(() -> new UserIdentityProjection(subjectId, "KEYCLOAK"));

            KeycloakUser kcUser = keycloak.fetchUser(subjectId);
            if (kcUser == null) {
                log.warn("Identity projection failed: Keycloak user not found subjectId={}", subjectId);
                return;
            }

            projection.update(
                    kcUser.username(),
                    kcUser.email(),
                    kcUser.displayName()
            );

            auditRepo.save(new IdentityProjectionAuditEvent(
                    subjectId,
                    ExecutionContext.SCHEDULED_JOB,
                    CorrelationSource.ADMIN_EVENT_ID,
                    AuditResult.SUCCESS,
                    "IDENTITY_PROJECTED"
            ));
            log.debug("Identity projection updated subjectId={}", subjectId);

        } catch (Exception ex) {
            log.error(
                    "IDENTITY PROJECTION FAILURE subjectId={}",
                    subjectId,
                    ex
            );
        }
    }
}
