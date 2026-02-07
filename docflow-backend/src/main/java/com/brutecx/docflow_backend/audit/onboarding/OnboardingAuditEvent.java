package com.brutecx.docflow_backend.audit.onboarding;

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

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(nullable = false, updatable = false, name = "user_agent", length = 512)
    private String userAgent;

    @Column(nullable = false, updatable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private OnboardingOutcome outcome;

    @Column(updatable = false, length = 64)
    private String failureReason;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 64)
    private String eventFingerprint;

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
            String ip,
            String userAgent,
            OnboardingOutcome outcome,
            String failureReason,
            String eventFingerprint
    ) {
        this.actorUserId = actorUserId;
        this.subjectId = subjectId;
        this.tenantId = tenantId;
        this.inviteId = inviteId;
        this.correlationId = correlationId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.outcome = outcome;
        this.failureReason = failureReason;
        this.eventFingerprint = eventFingerprint;
    }
}
