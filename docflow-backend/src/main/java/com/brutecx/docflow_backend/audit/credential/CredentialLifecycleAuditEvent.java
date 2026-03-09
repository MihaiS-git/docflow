package com.brutecx.docflow_backend.audit.credential;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.ChainSegmentAware;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "credential_lifecycle_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CredentialLifecycleAuditEvent implements ChainSegmentAware {

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

    @Column(name = "session_id", length = 128, updatable = false)
    private String sessionId;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "event_type", length = 64)
    private CredentialLifecycleEventType eventType;

    @Column(name = "required_action", length = 128, updatable = false)
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
    @Column(nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @Column(nullable = false, updatable = false, name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "reason_detail", length = 512, updatable = false)
    private String reasonDetail;

    @Column(nullable = false, updatable = false, unique = true, name = "event_fingerprint", length = 128)
    private String eventFingerprint;

    @Column(nullable = false, updatable = false, name = "chain_version")
    private int chainVersion;

    @Column(nullable = false, updatable = false, name = "prev_event_hash", length = 128)
    private String prevEventHash;

    @Column(nullable = false, updatable = false, name = "event_hash", length = 128)
    private String eventHash;

    @Column(name = "chain_segment_hash", length = 128, updatable = false)
    private String chainSegmentHash;

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
        this.timestamp = Objects.requireNonNull(timestamp);
        this.ip = requireNonBlank(ip);
        this.eventType = Objects.requireNonNull(eventType);
        this.correlationId = requireNonBlank(correlationId);
        this.correlationSource = Objects.requireNonNull(correlationSource);
        this.executionContext = Objects.requireNonNull(executionContext);
        this.result = Objects.requireNonNull(result);
        this.reasonCode = requireNonBlank(reasonCode);
        this.eventFingerprint = requireNonBlank(eventFingerprint);
        this.prevEventHash = requireNonBlank(prevEventHash);
        this.eventHash = requireNonBlank(eventHash);

        this.subjectExternalId = subjectExternalId;
        this.clientId = clientId;
        this.sessionId = sessionId;
        this.requiredAction = requiredAction;
        this.reasonDetail = reasonDetail;

        if (chainVersion <= 0) {
            throw new IllegalArgumentException("chainVersion must be > 0");
        }

        this.chainVersion = chainVersion;
    }

    @Override
    public void setChainSegmentHash(String chainSegmentHash) {
        this.chainSegmentHash = chainSegmentHash;
    }

    private static String requireNonBlank(String v) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException();
        }
        return v;
    }
}