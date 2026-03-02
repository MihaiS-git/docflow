package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.auth.*;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
@ConditionalOnProperty(
        prefix = "docflow.security.keycloak.admin",
        name = "enabled",
        havingValue = "true"
)
public class KeycloakAuthEventPullJob {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String CHECKPOINT_ID = "KEYCLOAK_ADMIN_EVENTS";

    private static final String STREAM = AuthenticationAuditCanonicalMaterialBuilder.STREAM;
    private static final String EXEC_CTX = ExecutionContext.ADMIN_API.name();

    private final KeycloakAdminClient keycloak;
    private final KeycloakEventCheckpointRepository checkpointRepo;
    private final AuthenticationEventRepository authEventRepo;
    private final AuditChainService auditChainService;
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditWriteFailureMetrics metrics;
    private final AuditPartitionResolver partitionResolver;

    // kept for deterministic JSON helper (unused today; retained for future troubleshooting)
    private final ObjectMapper objectMapper;

    public KeycloakAuthEventPullJob(
            KeycloakAdminClient keycloak,
            KeycloakEventCheckpointRepository checkpointRepo,
            AuthenticationEventRepository authEventRepo,
            AuditChainService auditChainService,
            AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder,
            AuditWriteFailureMetrics metrics,
            AuditPartitionResolver partitionResolver,
            ObjectMapper objectMapper
    ) {
        this.keycloak = keycloak;
        this.checkpointRepo = checkpointRepo;
        this.authEventRepo = authEventRepo;
        this.auditChainService = auditChainService;
        this.canonicalMaterialBuilder = canonicalMaterialBuilder;
        this.metrics = metrics;
        this.partitionResolver = partitionResolver;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
            initialDelayString = "${docflow.security.keycloak.admin.initial-delay-ms:30000}",
            fixedDelayString = "${docflow.security.keycloak.admin.poll-fixed-delay-ms}"
    )
    public void pull() {

        long since = checkpointRepo.findById(CHECKPOINT_ID)
                .map(KeycloakEventCheckpoint::getLastEventTimeMs)
                .orElse(0L);

        long maxPersistedTime = since;
        int duplicates = 0;

        final List<KeycloakAdminClient.KeycloakAdminEvent> events;
        try {
            events = keycloak.fetchEvents(since);
        } catch (HttpClientErrorException.Forbidden ex) {
            log.warn("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "identity"),
                    kv("event.action", "keycloak_admin_events_poll"),
                    kv("event.outcome", "skipped"),
                    kv("reason", "forbidden"),
                    kv("http.status_code", 403),
                    kv("execution.context", EXEC_CTX),
                    kv("audit.stream", STREAM),
                    ex
            );
            return;
        } catch (Exception ex) {
            log.error("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "identity"),
                    kv("event.action", "keycloak_admin_events_poll"),
                    kv("event.outcome", "failure"),
                    kv("execution.context", EXEC_CTX),
                    kv("audit.stream", STREAM),
                    kv("exception.class", ex.getClass().getSimpleName()),
                    ex
            );
            return;
        }

        if (events.isEmpty()) {
            return;
        }

        for (KeycloakAdminClient.KeycloakAdminEvent e : events) {

            if (!"LOGIN_ERROR".equalsIgnoreCase(e.type())) {
                continue;
            }

            long eventMs = e.time();
            Instant eventTime = Instant.ofEpochMilli(eventMs);

            String subjectId =
                    (e.userId() != null && !e.userId().isBlank())
                            ? e.userId()
                            : "UNKNOWN";

            String ip = (e.ipAddress() != null && !e.ipAddress().isBlank())
                    ? e.ipAddress()
                    : "UNKNOWN";

            String userAgent = "N/A";
            if (e.details() != null) {
                String ua = e.details().get("user_agent");
                if (ua != null && !ua.isBlank()) userAgent = ua;
            }

            String correlationId;
            CorrelationSource correlationSource;

            if (e.sessionId() != null && !e.sessionId().isBlank()) {
                correlationId = e.sessionId();
                correlationSource = CorrelationSource.SESSION_ID;
            } else {
                correlationId = CHECKPOINT_ID + ":" + eventMs;
                correlationSource = CorrelationSource.PULL_RUN;
            }

            AuthenticationFailureReason reason = mapKeycloakReason(e.error());

            AuthenticationAuditMetadata metadata =
                    new AuthenticationFailureMetadata(reason, e.error());

            String fingerprint = EventFingerprint.of(List.of(
                    STREAM,
                    "KEYCLOAK_ADMIN",
                    AuthenticationResult.FAILURE.name(),
                    subjectId,
                    String.valueOf(eventMs),
                    ip,
                    correlationId
            ));

            AuthenticationAuditCanonicalMaterialBuilder.Input canonicalInput =
                    new AuthenticationAuditCanonicalMaterialBuilder.Input(
                            eventTime,
                            AuthenticationEventSource.KEYCLOAK_ADMIN_EVENTS,
                            subjectId,
                            subjectId,
                            AuthenticationResult.FAILURE,
                            metadata,
                            "KEYCLOAK",
                            ip,
                            userAgent,
                            correlationId,
                            correlationSource.name(),
                            EXEC_CTX,
                            AuditResult.FAILED.name(),
                            fingerprint
                    );

            String canonicalMaterial =
                    canonicalMaterialBuilder.buildCanonicalMaterial(canonicalInput);

            AuditPartition partition =
                    partitionResolver.authentication(subjectId);

            final long startNs = System.nanoTime();
            try {

                // GOLD: nextHash + save inside SAME try
                AuditChainService.ChainHash chain =
                        auditChainService.nextHash(
                                partition,
                                canonicalMaterial
                        );

                AuthenticationEvent entity = new AuthenticationEvent(
                        canonicalInput.source(),
                        canonicalInput.timestamp(),
                        canonicalInput.username(),
                        canonicalInput.subjectId(),
                        canonicalInput.result(),
                        canonicalInput.idp(),
                        canonicalInput.ip(),
                        canonicalInput.userAgent(),
                        canonicalInput.correlationId(),
                        CorrelationSource.valueOf(canonicalInput.correlationSource()),
                        ExecutionContext.valueOf(canonicalInput.executionContext()),
                        AuditResult.valueOf(canonicalInput.auditResult()),
                        metadata,
                        canonicalInput.fingerprint(),
                        chain.chainVersion(),
                        chain.prevHash(),
                        chain.eventHash()
                );

                authEventRepo.save(entity);

                metrics.incrementSuccess(STREAM, EXEC_CTX);
                maxPersistedTime = Math.max(maxPersistedTime, eventMs);

            } catch (DataIntegrityViolationException ex) {

                duplicates++;
                metrics.incrementDedup(STREAM, EXEC_CTX);

            } catch (Exception ex) {

                metrics.incrementFailure(STREAM, EXEC_CTX, ex);

                log.error("security_event",
                        kv("schema_version", "docflow_siem_v1"),
                        kv("event.category", "audit"),
                        kv("event.action", "authentication_audit_record_failed"),
                        kv("event.outcome", "failure"),
                        kv("audit.stream", STREAM),
                        kv("audit.partition", "SUBJECT"),
                        kv("execution.context", EXEC_CTX),
                        kv("correlation.id", correlationId),
                        kv("correlation.source", correlationSource.name()),
                        kv("subject.id", subjectId),
                        kv("keycloak.event_type", e.type()),
                        kv("keycloak.error", e.error()),
                        kv("keycloak.session_id", e.sessionId() != null ? e.sessionId() : "-"),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );

            } finally {
                metrics.recordLatency(
                        STREAM,
                        EXEC_CTX,
                        Duration.ofNanos(System.nanoTime() - startNs)
                );
            }
        }

        if (duplicates > 0) {
            log.debug("auth_event duplicate_events_skipped={}", duplicates);
        }

        if (maxPersistedTime > since) {

            long nextCheckpointTime = maxPersistedTime + 1;

            KeycloakEventCheckpoint cp =
                    checkpointRepo.findById(CHECKPOINT_ID).orElse(null);

            if (cp == null) {
                cp = new KeycloakEventCheckpoint(CHECKPOINT_ID, nextCheckpointTime);
            } else {
                cp.update(nextCheckpointTime);
            }

            checkpointRepo.save(cp);
        }
    }

    private AuthenticationFailureReason mapKeycloakReason(String error) {
        if (error == null) return AuthenticationFailureReason.UNKNOWN;
        return switch (error) {
            case "invalid_user_credentials" -> AuthenticationFailureReason.INVALID_CREDENTIALS;
            case "user_disabled" -> AuthenticationFailureReason.ACCOUNT_DISABLED;
            case "user_not_found" -> AuthenticationFailureReason.USER_NOT_FOUND;
            default -> AuthenticationFailureReason.UNKNOWN;
        };
    }

    @SuppressWarnings("unused")
    private String toDeterministicJson(AuthenticationAuditMetadata metadata) {
        try {
            ObjectMapper m = objectMapper.copy()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            return m.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize AUTH metadata", ex);
        }
    }
}