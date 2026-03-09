package com.brutecx.docflow_backend.audit.onboarding;

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
@Table(name = "onboarding_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingAuditEvent implements ChainSegmentAware {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "subject_id", nullable = false, updatable = false, length = 128)
    private String subjectId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "invite_id", nullable = false, updatable = false)
    private UUID inviteId;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "correlation_source", nullable = false, updatable = false, length = 32)
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_context", nullable = false, updatable = false, length = 32)
    private ExecutionContext executionContext;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(name = "user_agent", nullable = false, updatable = false, length = 512)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private OnboardingOutcome outcome;

    @Column(name = "reason_code", nullable = false, updatable = false, length = 64)
    private String reasonCode;

    @Column(name = "reason_detail", updatable = false, length = 512)
    private String reasonDetail;

    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 128)
    private String eventFingerprint;

    @Column(name = "chain_version", nullable = false, updatable = false)
    private int chainVersion;

    @Column(name = "prev_event_hash", nullable = false, updatable = false, length = 128)
    private String prevEventHash;

    @Column(name = "event_hash", nullable = false, updatable = false, length = 128)
    private String eventHash;

    @Column(name = "chain_segment_hash", length = 128, updatable = false)
    private String chainSegmentHash;

    public OnboardingAuditEvent(
            Instant timestamp,
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            String ip,
            String userAgent,
            AuditResult result,
            OnboardingOutcome outcome,
            String reasonCode,
            String reasonDetail,
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.actorUserId = actorUserId;
        this.subjectId = requireNonBlank(subjectId, "subjectId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.inviteId = Objects.requireNonNull(inviteId, "inviteId must not be null");
        this.correlationId = requireNonBlank(correlationId, "correlationId");
        this.correlationSource = Objects.requireNonNull(correlationSource, "correlationSource must not be null");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext must not be null");
        this.ip = requireNonBlank(ip, "ip");
        this.userAgent = requireNonBlank(userAgent, "userAgent");
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.reasonCode = requireNonBlank(reasonCode, "reasonCode");
        this.reasonDetail = reasonDetail;
        this.eventFingerprint = requireNonBlank(eventFingerprint, "eventFingerprint");
        this.prevEventHash = requireNonBlank(prevEventHash, "prevEventHash");
        this.eventHash = requireNonBlank(eventHash, "eventHash");

        if (chainVersion <= 0) {
            throw new IllegalArgumentException("chainVersion must be > 0");
        }

        this.chainVersion = chainVersion;
    }

    @Override
    public void setChainSegmentHash(String chainSegmentHash) {
        this.chainSegmentHash = chainSegmentHash;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

}