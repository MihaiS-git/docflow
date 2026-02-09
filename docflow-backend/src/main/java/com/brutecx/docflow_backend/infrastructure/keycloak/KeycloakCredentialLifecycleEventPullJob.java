package com.brutecx.docflow_backend.infrastructure.keycloak;

import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.credential.*;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    private final KeycloakAdminClient keycloak;
    private final KeycloakEventCheckpointRepository checkpointRepo;
    private final CredentialLifecycleAuditEventRepository repository;

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
            CredentialLifecycleEventType type =
                    mapEventType(e);

            if (type == CredentialLifecycleEventType.UNKNOWN) {
                continue;
            }

            String sessionId =
                    (e.sessionId() != null && !e.sessionId().isBlank())
                            ? e.sessionId()
                            : "-";

            String fingerprint = EventFingerprint.of(List.of(
                    "CREDENTIAL",
                    type.name(),
                    e.userId(),
                    e.clientId(),
                    sessionId,
                    String.valueOf(e.time())
            ));

            String correlationId = (e.sessionId() != null && !e.sessionId().isBlank())
                    ? e.sessionId()
                    : UUID.randomUUID().toString();

            CredentialLifecycleAuditEvent entity =
                    new CredentialLifecycleAuditEvent(
                            Instant.ofEpochMilli(e.time()),
                            e.userId(),
                            e.clientId(),
                            e.sessionId(), // nullable is fine here
                            e.ipAddress() != null ? e.ipAddress() : "UNKNOWN",
                            type,
                            extractRequiredAction(e),
                            correlationId,
                            (e.sessionId() != null && !e.sessionId().isBlank())
                                    ? CorrelationSource.SESSION_ID
                                    : CorrelationSource.GENERATED,
                            ExecutionContext.SCHEDULED_JOB,
                            AuditResult.SUCCESS,
                            "CREDENTIAL_" + type.name(),
                            null,
                            fingerprint
                    );

            try {
                repository.save(entity);
                maxTime = Math.max(maxTime, e.time());
            } catch (DataIntegrityViolationException ex) {
                // deduplicated
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
        if (e.details() == null) return null;
        return e.details().get("required_action");
    }
}
