package com.brutecx.docflow_backend.audit.onboarding;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "onboarding_audit_events",
        indexes = {
                @Index(name = "idx_onboarding_invite_id", columnList = "invite_id"),
                @Index(name = "idx_onboarding_subject_id", columnList = "subject_id"),
                @Index(name = "idx_onboarding_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_onboarding_timestamp", columnList = "timestamp"),
                @Index(name = "idx_onboarding_correlation_id", columnList = "correlation_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_onboarding_invite_once",
                        columnNames = {"invite_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OnboardingAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(updatable = false, name = "actor_user_id")
    private UUID actorUserId;

    @Column(updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @Column(nullable = false, updatable = false, name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, updatable = false, name = "invite_id")
    private UUID inviteId;

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "correlation_source", nullable = false, updatable = false, length = 32)
    private CorrelationSource correlationSource;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_context", nullable = false, updatable = false, length = 32)
    private ExecutionContext executionContext;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(nullable = false, updatable = false, name = "user_agent", length = 512)
    private String userAgent;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @Column(nullable = false, updatable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private OnboardingOutcome outcome;

    @NotNull
    @Column(name = "reason_code", nullable = false, updatable = false, length = 64)
    private String reasonCode;

    @Column(name = "reason_detail", updatable = false, length = 512)
    private String reasonDetail;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 64)
    private String eventFingerprint;

    @NotNull
    @Column(name = "chain_version", nullable = false, updatable = false)
    private int chainVersion;

    @Column(name = "prev_event_hash", updatable = false, length = 64)
    private String prevEventHash;

    @NotNull
    @Column(name = "event_hash", nullable = false, updatable = false, length = 64)
    private String eventHash;

    @PrePersist
    void prePersist() {
        this.timestamp = Instant.now();
    }

    public OnboardingAuditEvent(
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
        this.actorUserId = actorUserId;
        this.subjectId = subjectId;
        this.tenantId = tenantId;
        this.inviteId = inviteId;
        this.correlationId = correlationId;
        this.correlationSource = correlationSource;
        this.executionContext = executionContext;
        this.ip = ip;
        this.userAgent = userAgent;
        this.result = result;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.eventFingerprint = eventFingerprint;
        this.chainVersion = chainVersion;
        this.prevEventHash = prevEventHash;
        this.eventHash = eventHash;
    }
}
