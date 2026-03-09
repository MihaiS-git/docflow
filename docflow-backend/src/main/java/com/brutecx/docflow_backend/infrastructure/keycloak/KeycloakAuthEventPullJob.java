package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.brutecx.docflow_backend.audit.AuditStreamExecutor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.auth.AuthenticationAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.auth.AuthenticationAuditMetadata;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventRepository;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventSource;
import com.brutecx.docflow_backend.audit.auth.AuthenticationFailureMetadata;
import com.brutecx.docflow_backend.audit.auth.AuthenticationFailureReason;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.tamper.AuditPartitionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

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
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditPartitionResolver partitionResolver;
    private final AuditStreamExecutor executor;

    public KeycloakAuthEventPullJob(
            KeycloakAdminClient keycloak,
            KeycloakEventCheckpointRepository checkpointRepo,
            AuthenticationEventRepository authEventRepo,
            AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder,
            AuditPartitionResolver partitionResolver,
            AuditStreamExecutor executor
    ) {
        this.keycloak = keycloak;
        this.checkpointRepo = checkpointRepo;
        this.authEventRepo = authEventRepo;
        this.canonicalMaterialBuilder = canonicalMaterialBuilder;
        this.partitionResolver = partitionResolver;
        this.executor = executor;
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

        final List<KeycloakAdminClient.KeycloakAdminEvent> events;
        try {
            events = keycloak.fetchEvents(since);
        } catch (HttpClientErrorException.Forbidden ex) {
            log.warn(
                    "security_event",
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
            log.error(
                    "security_event",
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

            String ip =
                    (e.ipAddress() != null && !e.ipAddress().isBlank())
                            ? e.ipAddress()
                            : "UNKNOWN";

            String userAgent = "N/A";
            if (e.details() != null) {
                String ua = e.details().get("user_agent");
                if (ua != null && !ua.isBlank()) {
                    userAgent = ua;
                }
            }

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

            try {
                AuditStreamExecutor.WriteOutcome outcome =
                        executor.execute(
                                STREAM,
                                EXEC_CTX,
                                partition,
                                canonicalMaterial,
                                authEventRepo,
                                prepared -> new AuthenticationEvent(
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
                                        prepared.chainVersion(),
                                        prepared.prevHash(),
                                        prepared.eventHash()
                                )
                        );

                if (outcome == AuditStreamExecutor.WriteOutcome.DEDUP) {
                    log.debug(
                            "security_event",
                            kv("event.category", "audit"),
                            kv("event.action", "auth_audit_deduplicated"),
                            kv("audit.stream", STREAM),
                            kv("subject.id", subjectId),
                            kv("correlation.id", correlationId),
                            kv("keycloak.event_type", e.type()),
                            kv("keycloak.error", e.error())
                    );
                }

                maxPersistedTime = Math.max(maxPersistedTime, eventMs);

            } catch (Exception ex) {
                log.error(
                        "security_event",
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
                        kv("keycloak.session_id", sessionId),
                        kv("exception.class", ex.getClass().getSimpleName()),
                        ex
                );
            }
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
        if (error == null) {
            return AuthenticationFailureReason.UNKNOWN;
        }

        return switch (error) {
            case "invalid_user_credentials" -> AuthenticationFailureReason.INVALID_CREDENTIALS;
            case "user_disabled" -> AuthenticationFailureReason.ACCOUNT_DISABLED;
            case "user_not_found" -> AuthenticationFailureReason.USER_NOT_FOUND;
            default -> AuthenticationFailureReason.UNKNOWN;
        };
    }

}