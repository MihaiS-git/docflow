package com.brutecx.docflow_backend.audit.identity;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserIdentityEnrichmentServiceImpl implements IUserIdentityProjectionService {

    private static final String STREAM = IdentityProjectionCanonicalMaterialBuilder.STREAM;

    private final UserIdentityProjectionRepository repo;
    private final KeycloakAdminClient keycloak;
    private final IdentityProjectionAuditEventRepository auditRepo;
    private final AuditChainService auditChainService;
    private final IdentityProjectionCanonicalMaterialBuilder canonicalBuilder;
    private final AuditWriteFailureMetrics metrics;

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

            Instant now = Instant.now();

            String correlationId = "identity-" + subjectId;

            String fingerprint = EventFingerprint.of(List.of(
                    STREAM,
                    subjectId,
                    kcUser.username(),
                    kcUser.email()
            ));

            IdentityProjectionCanonicalMaterialBuilder.Input input =
                    new IdentityProjectionCanonicalMaterialBuilder.Input(
                            now,
                            subjectId,
                            correlationId,
                            ExecutionContext.SCHEDULED_JOB.name(),
                            CorrelationSource.GENERATED.name(),
                            AuditResult.SUCCESS.name(),
                            "IDENTITY_PROJECTED",
                            fingerprint
                    );

            String canonicalMaterial =
                    canonicalBuilder.buildCanonicalMaterial(input);

            AuditPartition partition =
                    AuditPartition.subject(STREAM, subjectId);

            AuditChainService.ChainHash chain =
                    auditChainService.nextHash(partition, canonicalMaterial);

            IdentityProjectionAuditEvent event =
                    new IdentityProjectionAuditEvent(
                            now,
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
                        "IDENTITY_PROJECTION_AUDIT_DEDUP subjectId={} correlationId={}",
                        subjectId,
                        correlationId
                );
            }
        } catch (Exception ex) {
            metrics.increment(
                    STREAM,
                    ExecutionContext.SCHEDULED_JOB.name(),
                    ex
            );
            log.error("IDENTITY_PROJECTION_FAILURE subjectId={}", subjectId, ex);
        }
    }
}
