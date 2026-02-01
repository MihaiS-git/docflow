package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventRepository;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventSource;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job to pull authentication failure events from Keycloak admin API
 * and persist them into the local authentication event repository.
 * Only LOGIN_ERROR events are processed and stored.
 * The job maintains a checkpoint to avoid reprocessing events.
 * Handles duplicates gracefully and logs relevant information for auditing.
 * The job is enabled via configuration property.
 */
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

    public KeycloakAuthEventPullJob(
            KeycloakAdminClient keycloak,
            KeycloakEventCheckpointRepository checkpointRepo,
            AuthenticationEventRepository authEventRepo
    ) {
        this.keycloak = keycloak;
        this.checkpointRepo = checkpointRepo;
        this.authEventRepo = authEventRepo;
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

        List<KeycloakAdminClient.KeycloakAdminEvent> events;
        try {
            events = keycloak.fetchEvents(since);
        } catch (HttpClientErrorException.Forbidden ex) {
            // Admin client temporarily unauthorized (token expired / role missing).
            // This MUST NOT affect user login.
            log.warn("Keycloak admin events poll forbidden (403). Skipping this cycle.");
            return;
        } catch (Exception ex) {
            // Any other failure must also be isolated
            log.error("Failed to poll Keycloak admin events", ex);
            return;
        }

        if (events.isEmpty()) {
            return;
        }

        // persist only failures coming from Keycloak
        for (KeycloakAdminClient.KeycloakAdminEvent e : events) {
            if (!"LOGIN_ERROR".equalsIgnoreCase(e.type())) {
                continue;
            }

            String fingerprint = EventFingerprint.of(List.of(
                    e.type(),
                    String.valueOf(e.time()),
                    e.clientId() != null ? e.clientId() : "-",
                    e.userId() != null ? e.userId() : "-",
                    e.ipAddress() != null ? e.ipAddress() : "-",
                    e.sessionId() != null ? e.sessionId() : "-"
            ));

            String correlationId = (e.sessionId() != null && !e.sessionId().isBlank())
                    ? e.sessionId()
                    : UUID.randomUUID().toString();

            String username = (e.userId() != null && !e.userId().isBlank())
                    ? e.userId()
                    : "UNKNOWN";

            String userAgent = "N/A";
            if (e.details() != null) {
                String ua = e.details().get("user_agent");
                if (ua != null && !ua.isBlank()) userAgent = ua;

            }
            AuthenticationEvent entity = new AuthenticationEvent(
                    AuthenticationEventSource.KEYCLOAK_ADMIN_EVENTS,
                    Instant.ofEpochMilli(e.time()),
                    username,
                    AuthenticationResult.FAILURE,
                    "KEYCLOAK",
                    e.ipAddress() != null ? e.ipAddress() : "UNKNOWN",
                    userAgent,
                    correlationId,
                    fingerprint
            );

            try {
                authEventRepo.save(entity);
                maxPersistedTime = Math.max(maxPersistedTime, e.time());
            } catch (DataIntegrityViolationException ex) {
                // duplicate event → safe to ignore
                duplicates++;
                continue;
            }

            log.info(
                    "auth_event result={} username={} idp={} ip={} ua={} correlationId={}",
                    AuthenticationResult.FAILURE,
                    username,
                    "KEYCLOAK",
                    entity.getIp(),
                    entity.getUserAgent(),
                    correlationId
            );
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
