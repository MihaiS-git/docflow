package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.ChainSegmentAware;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditEvent implements ChainSegmentAware {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false, name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(nullable = false, updatable = false, name = "user_agent", length = 512)
    private String userAgent;

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "correlation_source")
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "execution_context")
    private ExecutionContext executionContext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditResult result;

    @Column(nullable = false, updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @Column(nullable = false, updatable = false, name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "action_type")
    private AdminAuditActionType actionType;

    @Column(updatable = false, name = "target_user_id")
    private UUID targetUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private AdminAuditMetadata metadata;

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

    public AdminAuditEvent(
            Instant timestamp,
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
        this.timestamp = Objects.requireNonNull(timestamp);
        this.actorUserId = Objects.requireNonNull(actorUserId);
        this.ip = requireNonBlank(ip);
        this.userAgent = requireNonBlank(userAgent);
        this.correlationId = requireNonBlank(correlationId);
        this.correlationSource = Objects.requireNonNull(correlationSource);
        this.executionContext = Objects.requireNonNull(executionContext);
        this.result = Objects.requireNonNull(result);
        this.subjectId = requireNonBlank(subjectId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.actionType = Objects.requireNonNull(actionType);
        this.eventFingerprint = requireNonBlank(eventFingerprint);
        this.prevEventHash = requireNonBlank(prevEventHash);
        this.eventHash = requireNonBlank(eventHash);

        if (chainVersion <= 0) {
            throw new IllegalArgumentException("chainVersion must be > 0");
        }

        this.chainVersion = chainVersion;
        this.targetUserId = targetUserId;
        this.metadata = metadata;
    }

    @Override
    public void setChainSegmentHash(String chainSegmentHash) {
        this.chainSegmentHash = chainSegmentHash;
    }

    private static String requireNonBlank(String v) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Field must not be blank");
        }
        return v;
    }

}