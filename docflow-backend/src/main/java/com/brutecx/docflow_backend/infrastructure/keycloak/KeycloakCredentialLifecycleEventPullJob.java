package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEventRepository;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleEventType;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "docflow.security.keycloak.admin",
        name = "enabled",
        havingValue = "true"
)
public class KeycloakCredentialLifecycleEventPullJob {

    private static final String CHECKPOINT_ID = "KEYCLOAK_CREDENTIAL_EVENTS";
    private static final String STREAM = CredentialLifecycleCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.SCHEDULED_JOB.name();

    private final KeycloakAdminClient keycloak;
    private final KeycloakEventCheckpointRepository checkpointRepo;
    private final CredentialLifecycleAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final CredentialLifecycleCanonicalMaterialBuilder canonicalBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final AuditPartitionResolver partitionResolver;

    @Scheduled(
            initialDelayString = "${docflow.security.keycloak.admin.initial-delay-ms:30000}",
            fixedDelayString = "${docflow.security.keycloak.admin.poll-fixed-delay-ms}"
    )
    public void pull() {

        long since = checkpointRepo.findById(CHECKPOINT_ID)
                .map(KeycloakEventCheckpoint::getLastEventTimeMs)
                .orElse(0L);

        long maxTime = since;

        List<KeycloakAdminClient.KeycloakAdminEvent> events =
                keycloak.fetchEvents(since);

        for (var e : events) {

            CredentialLifecycleEventType type = mapEventType(e);
            if (type == CredentialLifecycleEventType.UNKNOWN) {
                continue;
            }

            Instant eventTimestamp = Instant.ofEpochMilli(e.time());

            String sessionId =
                    (e.sessionId() != null && !e.sessionId().isBlank())
                            ? e.sessionId()
                            : "-";

            String correlationId;
            CorrelationSource correlationSource;

            if (!"-".equals(sessionId)) {
                correlationId = sessionId;
                correlationSource = CorrelationSource.SESSION_ID;
            } else {
                correlationId = CHECKPOINT_ID + ":" + e.time();
                correlationSource = CorrelationSource.PULL_RUN;
            }

            String fingerprint = EventFingerprint.of(List.of(
                    STREAM,
                    type.name(),
                    e.userId(),
                    e.clientId(),
                    sessionId,
                    String.valueOf(e.time())
            ));

            CredentialLifecycleCanonicalMaterialBuilder.Input input =
                    new CredentialLifecycleCanonicalMaterialBuilder.Input(
                            eventTimestamp,
                            e.userId(),
                            e.clientId(),
                            sessionId,
                            e.ipAddress() != null ? e.ipAddress() : "UNKNOWN",
                            type,
                            extractRequiredAction(e),
                            correlationId,
                            correlationSource.name(),
                            EXEC_CTX,
                            AuditResult.SUCCESS.name(),
                            "CREDENTIAL_" + type.name(),
                            null,
                            fingerprint
                    );

            String canonicalMaterial =
                    canonicalBuilder.buildCanonicalMaterial(input);

            AuditPartition partition =
                    partitionResolver.credentialLifecycle(e.userId());

            final long startNs = System.nanoTime();
            try {

                AuditChainService.ChainHash chain =
                        auditChainService.nextHash(partition, canonicalMaterial);

                CredentialLifecycleAuditEvent entity =
                        new CredentialLifecycleAuditEvent(
                                input.timestamp(),
                                input.subjectExternalId(),
                                input.clientId(),
                                input.sessionId(),
                                input.ip(),
                                input.eventType(),
                                input.requiredAction(),
                                input.correlationId(),
                                CorrelationSource.valueOf(input.correlationSource()),
                                ExecutionContext.valueOf(input.executionContext()),
                                AuditResult.valueOf(input.result()),
                                input.reasonCode(),
                                input.reasonDetail(),
                                input.fingerprint(),
                                chain.chainVersion(),
                                chain.prevHash(),
                                chain.eventHash()
                        );

                repository.save(entity);

                metrics.incrementSuccess(STREAM, EXEC_CTX);
                metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

                maxTime = Math.max(maxTime, e.time());

            } catch (DataIntegrityViolationException ignored) {
                metrics.incrementDedup(STREAM, EXEC_CTX);
                metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

            } catch (Exception ex) {

                metrics.incrementFailure(STREAM, EXEC_CTX, ex);
                metrics.recordLatency(STREAM, EXEC_CTX, Duration.ofNanos(System.nanoTime() - startNs));

                log.error("security_event",
                        kv("schema_version", "docflow_siem_v1"),
                        kv("event.category", "audit"),
                        kv("event.action", "credential_lifecycle_audit_record_failed"),
                        kv("event.outcome", "failure"),
                        kv("audit.stream", STREAM),
                        kv("audit.partition", "SUBJECT"),
                        kv("audit.event_type", type.name()),
                        kv("execution.context", EXEC_CTX),
                        kv("correlation.id", correlationId),
                        kv("correlation.source", correlationSource.name()),
                        kv("subject.id", e.userId()),
                        kv("client.id", e.clientId()),
                        kv("keycloak.session_id", sessionId),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );
            }
        }

        if (maxTime > since) {
            final long checkpointTime = maxTime + 1;
            checkpointRepo.save(
                    checkpointRepo.findById(CHECKPOINT_ID)
                            .map(cp -> {
                                cp.update(checkpointTime);
                                return cp;
                            })
                            .orElse(new KeycloakEventCheckpoint(
                                    CHECKPOINT_ID,
                                    checkpointTime
                            ))
            );
        }
    }

    private CredentialLifecycleEventType mapEventType(
            KeycloakAdminClient.KeycloakAdminEvent e
    ) {
        if ("UPDATE_PASSWORD".equalsIgnoreCase(e.type())) {
            return CredentialLifecycleEventType.PASSWORD_CHANGED;
        }
        if ("UPDATE_TOTP".equalsIgnoreCase(e.type())) {
            return CredentialLifecycleEventType.MFA_ENROLLED;
        }
        if ("REMOVE_TOTP".equalsIgnoreCase(e.type())) {
            return CredentialLifecycleEventType.MFA_REMOVED;
        }
        return CredentialLifecycleEventType.UNKNOWN;
    }

    private String extractRequiredAction(
            KeycloakAdminClient.KeycloakAdminEvent e
    ) {
        if (e.details() == null) {
            return null;
        }
        return e.details().get("required_action");
    }
}