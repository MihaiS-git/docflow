package com.brutecx.docflow_backend.audit.sensitive;

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
        name = "sensitive_access_audit_events",
        indexes = {
                @Index(name = "idx_sensitive_access_actor_user_id", columnList = "actor_user_id"),
                @Index(name = "idx_sensitive_access_subject_id", columnList = "subject_id"),
                @Index(name = "idx_sensitive_access_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_sensitive_access_resource", columnList = "resource"),
                @Index(name = "idx_sensitive_access_timestamp", columnList = "timestamp"),
                @Index(name = "idx_sensitive_access_correlation_id", columnList = "correlation_id"),
                @Index(name = "ux_sensitive_access_fingerprint", columnList = "event_fingerprint", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensitiveAccessAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    /* =========================
       ACTOR / IDENTITY CONTEXT
       ========================= */

    @Column(updatable = false, name = "actor_user_id")
    private UUID actorUserId;

    @Column(updatable = false, name = "actor_external_subject_id", length = 128)
    private String actorExternalSubjectId;

    @Column(updatable = false, name = "tenant_id")
    private UUID tenantId;

    /* =========================
       SUBJECT / RESOURCE
       ========================= */

    @Column(nullable = false, updatable = false, name = "subject_type", length = 64)
    @Enumerated(EnumType.STRING)
    private SensitiveAccessSubjectType subjectType;

    @Column(updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @Column(nullable = false, updatable = false, length = 128)
    private String resource;

    @Column(nullable = false, updatable = false, length = 64)
    private String action;

    @Column(updatable = false, name = "resource_path", length = 512)
    private String resourcePath;

    /* =========================
       REQUEST CONTEXT
       ========================= */

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    @Column(nullable = false, updatable = false, name = "correlation_source", length = 32)
    @Enumerated(EnumType.STRING)
    private CorrelationSource correlationSource;

    @Column(nullable = false, updatable = false, name = "execution_context", length = 32)
    @Enumerated(EnumType.STRING)
    private ExecutionContext executionContext;

    @Column(nullable = false, updatable = false, name = "result", length = 16)
    @Enumerated(EnumType.STRING)
    private AuditResult result;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(nullable = false, updatable = false, name = "user_agent", length = 512)
    private String userAgent;

    /* =========================
       CLASSIFICATION
       ========================= */

    @Column(nullable = false, updatable = false, name = "reason_code", length = 64)
    private String reasonCode;

    @Column(updatable = false, name = "reason_detail", length = 512)
    private String reasonDetail;

    @Column(nullable = false, updatable = false, name = "data_classification", length = 32)
    @Enumerated(EnumType.STRING)
    private SensitiveDataClassification dataClassification;

    /* =========================
       INTEGRITY / IDEMPOTENCY
       ========================= */

    @Column(nullable = false, updatable = false, name = "event_fingerprint", length = 128)
    private String eventFingerprint;

    /* =========================
       LIFECYCLE
       ========================= */

    @PrePersist
    void prePersist() {
        this.timestamp = Instant.now();
    }

    public SensitiveAccessAuditEvent(
            UUID actorUserId,
            String actorExternalSubjectId,
            UUID tenantId,
            SensitiveAccessSubjectType subjectType,
            String subjectId,
            String resource,
            String action,
            String resourcePath,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result,
            String ip,
            String userAgent,
            String reasonCode,
            String reasonDetail,
            SensitiveDataClassification dataClassification,
            String eventFingerprint
            ) {
        this.actorUserId = actorUserId;
        this.actorExternalSubjectId = actorExternalSubjectId;
        this.tenantId = tenantId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.resource = resource;
        this.action = action;
        this.resourcePath = resourcePath;
        this.correlationId = correlationId;
        this.correlationSource = correlationSource;
        this.executionContext = executionContext;
        this.result = result;
        this.ip = ip;
        this.userAgent = userAgent;
        this.reasonCode = reasonCode;
        this.reasonDetail = reasonDetail;
        this.dataClassification = dataClassification;
        this.eventFingerprint = eventFingerprint;
    }
}
