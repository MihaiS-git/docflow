package com.brutecx.docflow_backend.audit.credential;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "credential_lifecycle_audit_events",
        indexes = {
                @Index(name = "idx_cred_lifecycle_ts", columnList = "timestamp"),
                @Index(name = "idx_cred_lifecycle_subject", columnList = "subject_external_id"),
                @Index(name = "idx_cred_lifecycle_correlation", columnList = "correlation_id"),
                @Index(
                        name = "ux_cred_lifecycle_fingerprint",
                        columnList = "event_fingerprint",
                        unique = true
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredentialLifecycleAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    /* =========================
       SUBJECT (KEYCLOAK)
       ========================= */

    @Column(name = "subject_external_id", length = 128, updatable = false)
    private String subjectExternalId; // Keycloak userId

    @Column(name = "client_id", length = 128, updatable = false)
    private String clientId;

    @Column(length = 128, updatable = false)
    private String sessionId;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    /* =========================
       CREDENTIAL LIFECYCLE
       ========================= */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private CredentialLifecycleEventType eventType;

    @Column(length = 128, updatable = false)
    private String requiredAction;

    /* =========================
       REQUEST CONTEXT
       ========================= */

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    /* =========================
       IDEMPOTENCY
       ========================= */

    @Column(nullable = false, updatable = false, name = "event_fingerprint", length = 128)
    private String eventFingerprint;

    public CredentialLifecycleAuditEvent(
            Instant timestamp,
            String subjectExternalId,
            String clientId,
            String sessionId,
            String ip,
            CredentialLifecycleEventType eventType,
            String requiredAction,
            String correlationId,
            String eventFingerprint
    ) {
        this.timestamp = timestamp;
        this.subjectExternalId = subjectExternalId;
        this.clientId = clientId;
        this.sessionId = sessionId;
        this.ip = ip;
        this.eventType = eventType;
        this.requiredAction = requiredAction;
        this.correlationId = correlationId;
        this.eventFingerprint = eventFingerprint;
    }
}
