package com.brutecx.docflow_backend.audit.onboarding;

import jakarta.persistence.*;
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
                @Index(name = "idx_onboarding_timestamp", columnList = "timestamp")
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

    @Column(nullable = false, updatable = false, name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @Column(nullable = false, updatable = false, name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, updatable = false, name = "invite_id")
    private UUID inviteId;

    @Column(nullable = false, updatable = false, name = "request_id", length = 128)
    private String requestId;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(nullable = false, updatable = false, name = "user_agent", length = 512)
    private String userAgent;

    @PrePersist
    void prePersist() {
        this.timestamp = Instant.now();
    }

    public OnboardingAuditEvent(
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
            String requestId,
            String ip,
            String userAgent
    ) {
        this.actorUserId = actorUserId;
        this.subjectId = subjectId;
        this.tenantId = tenantId;
        this.inviteId = inviteId;
        this.requestId = requestId;
        this.ip = ip;
        this.userAgent = userAgent;
    }
}
