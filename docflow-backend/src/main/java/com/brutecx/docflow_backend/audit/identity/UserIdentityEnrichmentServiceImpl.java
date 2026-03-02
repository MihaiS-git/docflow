package com.brutecx.docflow_backend.audit.identity;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class UserIdentityEnrichmentServiceImpl implements IUserIdentityProjectionService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String STREAM = IdentityProjectionCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.SCHEDULED_JOB.name();

    private final UserIdentityProjectionRepository repo;
    private final KeycloakAdminClient keycloak;
    private final IdentityProjectionAuditEventRepository auditRepo;
    private final AuditChainService auditChainService;
    private final IdentityProjectionCanonicalMaterialBuilder canonicalBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final AuditPartitionResolver partitionResolver;

    @Async
    @Transactional
    @Override
    public void ensureProjected(String subjectId) {

        if (subjectId == null || "UNKNOWN".equals(subjectId)) {
            return;
        }

        final String correlationId = "identity-" + subjectId;
        final long startNs = System.nanoTime();

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
                log.warn("security_event",
                        kv("schema_version", "docflow_siem_v1"),
                        kv("event.category", "audit"),
                        kv("event.action", "identity_projection_skipped"),
                        kv("event.outcome", "failure"),
                        kv("audit.stream", STREAM),
                        kv("execution.context", EXEC_CTX),
                        kv("correlation.id", correlationId),
                        kv("correlation.source", CorrelationSource.GENERATED.name()),
                        kv("subject.id", subjectId),
                        kv("error.reason", "keycloak_user_not_found")
                );

                return;
            }

            projection.update(
                    kcUser.username(),
                    kcUser.email(),
                    kcUser.displayName()
            );

            repo.save(projection);

            Instant now = Instant.now();

            String fingerprint = EventFingerprint.of(List.of(
                    STREAM,
                    subjectId,
                    safe(kcUser.username()),
                    safe(kcUser.displayName())
            ));

            IdentityProjectionCanonicalMaterialBuilder.Input input =
                    new IdentityProjectionCanonicalMaterialBuilder.Input(
                            now,
                            subjectId,
                            correlationId,
                            EXEC_CTX,
                            CorrelationSource.GENERATED.name(),
                            AuditResult.SUCCESS.name(),
                            "IDENTITY_PROJECTED",
                            fingerprint
                    );

            String canonicalMaterial =
                    canonicalBuilder.buildCanonicalMaterial(input);

            AuditPartition partition =
                    partitionResolver.identityProjection(subjectId);

            try {
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

                auditRepo.save(event);

                metrics.incrementSuccess(STREAM, EXEC_CTX);
            } catch (DataIntegrityViolationException ignored) {
                metrics.incrementDedup(STREAM, EXEC_CTX);
            }

            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));
        } catch (Exception ex) {
            metrics.incrementFailure(STREAM, EXEC_CTX, ex);
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));

            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "identity_projection_failed"),
                    kv("event.outcome", "failure"),
                    kv("audit.stream", STREAM),
                    kv("execution.context", EXEC_CTX),
                    kv("correlation.id", correlationId),
                    kv("correlation.source", CorrelationSource.GENERATED.name()),
                    kv("subject.id", subjectId),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
        }
    }

    private static String safe(String v) {
        return (v == null || v.isBlank()) ? "-" : v.trim();
    }
}