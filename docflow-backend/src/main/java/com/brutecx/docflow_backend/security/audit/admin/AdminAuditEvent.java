package com.brutecx.docflow_backend.security.audit.admin;

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
@Table(name = "admin_audit_events")
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
    @Column(nullable = false, updatable = false, name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Column(nullable = false, updatable = false, name = "action_type")
    @Enumerated(EnumType.STRING)
    private AdminAuditActionType actionType;

    @NotNull
    @Column(nullable = false, updatable = false, name = "target_user_id")
    private UUID targetUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private AdminAuditMetadata metadata;

    public AdminAuditEvent(
            UUID actorUserId,
            UUID tenantId,
            AdminAuditActionType actionType,
            UUID targetUserId,
            AdminAuditMetadata metadata
    ) {
        this.actorUserId = actorUserId;
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
