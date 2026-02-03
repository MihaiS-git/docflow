package com.brutecx.docflow_backend.audit.admin;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "admin_audit_events",
        indexes = {
                @Index(name = "idx_admin_audit_timestamp", columnList = "timestamp"),
                @Index(name = "idx_admin_audit_actor_user_id", columnList = "actor_user_id"),
                @Index(name = "idx_admin_audit_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_admin_audit_request_id", columnList = "request_id"),
                @Index(name = "idx_admin_audit_subject_id", columnList = "subject_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @NotNull
    @Column(nullable = false, updatable = false, name = "actor_user_id")
    private UUID actorUserId;

    @NotNull
    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @NotNull
    @Column(nullable = false, updatable = false, name = "user_agent", length = 512)
    private String userAgent;

    @NotNull
    @Column(nullable = false, updatable = false, name = "request_id", length = 128)
    private String requestId;

    @NotNull
    @Column(nullable = false, updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @NotNull
    @Column(nullable = false, updatable = false, name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Column(nullable = false, updatable = false, name = "action_type", length = 64)
    @Enumerated(EnumType.STRING)
    private AdminAuditActionType actionType;

    @Column(updatable = false, name = "target_user_id")
    private UUID targetUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private AdminAuditMetadata metadata;

    public AdminAuditEvent(
            UUID actorUserId,
            String ip,
            String userAgent,
            String requestId,
            String subjectId,
            UUID tenantId,
            AdminAuditActionType actionType,
            UUID targetUserId,
            AdminAuditMetadata metadata
    ) {
        this.actorUserId = actorUserId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.subjectId = subjectId;
        this.tenantId = tenantId;
        this.actionType = actionType;
        this.targetUserId = targetUserId;
        this.metadata = metadata;
    }

    @PrePersist
    private void prePersist() {
        this.timestamp = Instant.now();
    }
}
