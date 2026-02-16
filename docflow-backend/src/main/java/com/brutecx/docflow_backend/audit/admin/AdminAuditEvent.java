package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
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
                @Index(name = "idx_admin_audit_ts_id", columnList = "timestamp,id"),
                @Index(name = "idx_admin_audit_tenant_ts_id", columnList = "tenant_id,timestamp,id"),
                @Index(name = "idx_admin_audit_actor", columnList = "actor_user_id"),
                @Index(name = "idx_admin_audit_correlation", columnList = "correlation_id")
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
    @Column(nullable = false, updatable = false)
    private String ip;

    @NotNull
    @Column(nullable = false, updatable = false, name = "user_agent")
    private String userAgent;

    @NotNull
    @Column(nullable = false, updatable = false, name = "correlation_id")
    private String correlationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "correlation_source")
    private CorrelationSource correlationSource;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "execution_context")
    private ExecutionContext executionContext;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditResult result;

    @NotNull
    @Column(nullable = false, updatable = false, name = "subject_id")
    private String subjectId;

    @NotNull
    @Column(nullable = false, updatable = false, name = "tenant_id")
    private UUID tenantId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "action_type")
    private AdminAuditActionType actionType;

    @Column(updatable = false, name = "target_user_id")
    private UUID targetUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private AdminAuditMetadata metadata;

    @NotNull
    @Column(nullable = false, updatable = false, unique = true)
    private String eventFingerprint;

    @NotNull
    @Column(nullable = false, updatable = false)
    private int chainVersion;

    @NotNull
    @Column(nullable = false, updatable = false)
    private String prevEventHash;

    @NotNull
    @Column(nullable = false, updatable = false)
    private String eventHash;

    public AdminAuditEvent(
            UUID actorUserId,
            String ip,
            String userAgent,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result,
            String subjectId,
            UUID tenantId,
            AdminAuditActionType actionType,
            UUID targetUserId,
            AdminAuditMetadata metadata,
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {
        this.actorUserId = actorUserId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.correlationSource = correlationSource;
        this.executionContext = executionContext;
        this.result = result;
        this.subjectId = subjectId;
        this.tenantId = tenantId;
        this.actionType = actionType;
        this.targetUserId = targetUserId;
        this.metadata = metadata;
        this.eventFingerprint = eventFingerprint;
        this.chainVersion = chainVersion;
        this.prevEventHash = prevEventHash;
        this.eventHash = eventHash;
    }

    @PrePersist
    private void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }
}
