package com.brutecx.docflow_backend.audit.identity;

import com.brutecx.docflow_backend.audit.AuditStreamExecutor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakAdminClient;
import com.brutecx.docflow_backend.infrastructure.keycloak.KeycloakUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final IdentityProjectionCanonicalMaterialBuilder canonicalBuilder;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    @Async
    @Transactional
    @Override
    public void ensureProjected(String subjectId) {
        if (subjectId == null || "UNKNOWN".equals(subjectId)) {
            return;
        }

        final String correlationId = "identity-" + subjectId;

        try {
            Optional<UserIdentityProjection> existingOpt = repo.findById(subjectId);

            if (existingOpt.isPresent()) {
                UserIdentityProjection existing = existingOpt.get();
                if (existing.isInitialized() && existing.isFresh(Duration.ofHours(24))) {
                    return;
                }
            }

            KeycloakUser kcUser = keycloak.fetchUser(subjectId);

            if (kcUser == null) {
                log.warn(
                        "security_event",
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

            UserIdentityProjection projection =
                    existingOpt.orElseGet(() -> new UserIdentityProjection(subjectId, "KEYCLOAK"));

            String normalizedUsername = safe(kcUser.username());
            String normalizedEmail = safe(kcUser.email());
            String normalizedDisplayName = safe(kcUser.displayName());

            boolean initialized = projection.isInitialized();
            boolean changed =
                    !normalizedUsername.equals(safe(projection.getUsername()))
                            || !normalizedEmail.equals(safe(projection.getEmail()))
                            || !normalizedDisplayName.equals(safe(projection.getDisplayName()));

            if (initialized && !changed) {
                return;
            }

            projection.update(
                    kcUser.username(),
                    kcUser.email(),
                    kcUser.displayName()
            );

            repo.saveAndFlush(projection);

            Instant now = Instant.now();

            String fingerprint = EventFingerprint.of(List.of(
                    STREAM,
                    "IDENTITY_PROJECTED",
                    subjectId,
                    "KEYCLOAK",
                    normalizedUsername,
                    normalizedDisplayName
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

            String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(input);

            AuditPartition partition = partitionResolver.identityProjection(subjectId);

            AuditStreamExecutor.WriteOutcome outcome =
                    executor.execute(
                            STREAM,
                            EXEC_CTX,
                            partition,
                            canonicalMaterial,
                            auditRepo,
                            prepared -> new IdentityProjectionAuditEvent(
                                    now,
                                    subjectId,
                                    correlationId,
                                    ExecutionContext.SCHEDULED_JOB,
                                    CorrelationSource.GENERATED,
                                    AuditResult.SUCCESS,
                                    "IDENTITY_PROJECTED",
                                    fingerprint,
                                    prepared.chainVersion(),
                                    prepared.prevHash(),
                                    prepared.eventHash()
                            )
                    );

            if (outcome == AuditStreamExecutor.WriteOutcome.DEDUP) {
                log.debug(
                        "security_event",
                        kv("event.category", "audit"),
                        kv("event.action", "identity_projection_deduplicated"),
                        kv("audit.stream", STREAM),
                        kv("subject.id", subjectId)
                );
            }
        } catch (Exception ex) {
            log.error(
                    "security_event",
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