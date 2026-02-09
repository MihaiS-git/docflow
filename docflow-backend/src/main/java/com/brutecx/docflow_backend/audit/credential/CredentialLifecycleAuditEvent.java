package com.brutecx.docflow_backend.audit.credential;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
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

    @Column(name = "subject_external_id", length = 128, updatable = false)
    private String subjectExternalId;

    @Column(name = "client_id", length = 128, updatable = false)
    private String clientId;

    @Column(length = 128, updatable = false)
    private String sessionId;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private CredentialLifecycleEventType eventType;

    @Column(length = 128, updatable = false)
    private String requiredAction;

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "correlation_source", length = 32)
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "execution_context", length = 32)
    private ExecutionContext executionContext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "result", length = 16)
    private AuditResult result;

    @Column(nullable = false, updatable = false, name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "reason_detail", updatable = false, length = 512)
    private String reasonDetail;

    @Column(nullable = false, updatable = false, name = "event_fingerprint", length = 128)
    private String eventFingerprint;

    @Column(name = "chain_version", nullable = false, updatable = false)
    private int chainVersion;

    @Column(name = "prev_event_hash", updatable = false, length = 64)
    private String prevEventHash;

    @Column(name = "event_hash", nullable = false, updatable = false, length = 64)
    private String eventHash;

    public CredentialLifecycleAuditEvent(
            Instant timestamp,
            String subjectExternalId,
            String clientId,
            String sessionId,
            String ip,
            CredentialLifecycleEventType eventType,
            String requiredAction,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result,
            String reasonCode,
            String reasonDetail,
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {
        this.timestamp = timestamp;
        this.subjectExternalId = subjectExternalId;
        this.clientId = clientId;
        this.sessionId = sessionId;
        this.ip = ip;
        this.eventType = eventType;
        this.requiredAction = requiredAction;
        this.correlationId = correlationId;
        this.correlationSource = correlationSource;
        this.executionContext = executionContext;
        this.result = result;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.eventFingerprint = eventFingerprint;
        this.chainVersion = chainVersion;
        this.prevEventHash = prevEventHash;
        this.eventHash = eventHash;
    }
}
