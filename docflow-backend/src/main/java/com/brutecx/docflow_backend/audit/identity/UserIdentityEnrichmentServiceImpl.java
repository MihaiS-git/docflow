package com.brutecx.docflow_backend.audit.identity;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserIdentityEnrichmentServiceImpl implements IUserIdentityProjectionService {

    private static final String STREAM = "IDENTITY_PROJECTION";

    private final UserIdentityProjectionRepository repo;
    private final KeycloakAdminClient keycloak;
    private final IdentityProjectionAuditEventRepository auditRepo;
    private final AuditChainService auditChainService;

    @Async
    @Transactional
    @Override
    public void ensureProjected(String subjectId) {

        if (subjectId == null || "UNKNOWN".equals(subjectId)) {
            return;
        }

        try {
            Optional<UserIdentityProjection> existingOpt = repo.findById(subjectId);

            if (existingOpt.isPresent()) {
                UserIdentityProjection existing = existingOpt.get();
                if (existing.isInitialized() && existing.isFresh(Duration.ofHours(24))) {
                    return;
                }
            }

            UserIdentityProjection projection =
                    existingOpt.orElseGet(() -> new UserIdentityProjection(subjectId, "KEYCLOAK"));

            KeycloakUser kcUser = keycloak.fetchUser(subjectId);
            if (kcUser == null) {
                log.warn("Identity projection skipped — Keycloak user not found subjectId={}", subjectId);
                return;
            }

            projection.update(
                    kcUser.username(),
                    kcUser.email(),
                    kcUser.displayName()
            );

            repo.save(projection);

            String correlationId = "identity-" + subjectId;

            String username = kcUser.username() != null ? kcUser.username() : "-";
            String email = kcUser.email() != null ? kcUser.email() : "-";

            String fingerprint = EventFingerprint.of(List.of(
                    STREAM,
                    subjectId,
                    username,
                    email
            ));

            String material = String.join("|",
                    STREAM,
                    subjectId,
                    username,
                    email,
                    fingerprint
            );

            /*
             * Partition rule:
             * Identity projection → SUBJECT
             */

            AuditPartition partition =
                    AuditPartition.subject(STREAM, subjectId);

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(
                            partition,
                            material
                    );

            IdentityProjectionAuditEvent event =
                    new IdentityProjectionAuditEvent(
                            subjectId,
                            correlationId,
                            ExecutionContext.SCHEDULED_JOB,
                            CorrelationSource.GENERATED,
                            AuditResult.SUCCESS,
                            "IDENTITY_PROJECTED",
                            fingerprint,
                            chain.chainVersion(),
                            chain.prevHash(),
                            chain.eventHash()
                    );

            try {
                auditRepo.save(event);
            } catch (DataIntegrityViolationException ex) {
                log.debug(
                        "IDENTITY PROJECTION AUDIT DEDUPLICATED subjectId={} correlationId={}",
                        subjectId,
                        correlationId
                );
            }

        } catch (Exception ex) {
            log.error("IDENTITY PROJECTION FAILURE subjectId={}", subjectId, ex);
        }
    }
}
