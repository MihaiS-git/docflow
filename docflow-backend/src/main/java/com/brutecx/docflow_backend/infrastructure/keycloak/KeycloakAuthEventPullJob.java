package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.auth.*;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "docflow.security.keycloak.admin",
        name = "enabled",
        havingValue = "true"
)
public class KeycloakAuthEventPullJob {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final String CHECKPOINT_ID = "KEYCLOAK_ADMIN_EVENTS";

    private final KeycloakAdminClient keycloak;
    private final KeycloakEventCheckpointRepository checkpointRepo;
    private final AuthenticationEventRepository authEventRepo;
    private final AuditChainService auditChainService;
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;

    public KeycloakAuthEventPullJob(
            KeycloakAdminClient keycloak,
            KeycloakEventCheckpointRepository checkpointRepo,
            AuthenticationEventRepository authEventRepo,
            AuditChainService auditChainService,
            AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder
    ) {
        this.keycloak = keycloak;
        this.checkpointRepo = checkpointRepo;
        this.authEventRepo = authEventRepo;
        this.auditChainService = auditChainService;
        this.canonicalMaterialBuilder = canonicalMaterialBuilder;
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
            log.warn("Keycloak admin events poll forbidden (403). Skipping this cycle.");
            return;
        } catch (Exception ex) {
            log.error("Failed to poll Keycloak admin events", ex);
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

            String username = subjectId;

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

            String fingerprint = EventFingerprint.of(List.of(
                    AuthenticationAuditCanonicalMaterialBuilder.STREAM,
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
                            username,
                            subjectId,
                            AuthenticationResult.FAILURE,
                            "KEYCLOAK",
                            ip,
                            userAgent,
                            correlationId,
                            correlationSource.name(),
                            ExecutionContext.ADMIN_API.name(),
                            AuditResult.FAILED.name(),
                            fingerprint
                    );

            String canonicalMaterial =
                    canonicalMaterialBuilder.buildCanonicalMaterial(canonicalInput);


            /*
             * Authentication → SUBJECT partition (per final rules)
             */

            AuditPartition partition =
                    AuditPartition.subject(
                            AuthenticationAuditCanonicalMaterialBuilder.STREAM,
                            subjectId
                    );

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
                    canonicalInput.fingerprint(),
                    chain.chainVersion(),
                    chain.prevHash(),
                    chain.eventHash()
            );


            try {
                authEventRepo.save(entity);
                maxPersistedTime = Math.max(maxPersistedTime, eventMs);
            } catch (DataIntegrityViolationException ex) {
                duplicates++;
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
}
