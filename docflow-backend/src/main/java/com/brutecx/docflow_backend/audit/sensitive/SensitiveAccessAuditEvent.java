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
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "sensitive_access_audit_events",
        indexes = {
                @Index(name = "idx_sensitive_access_actor_user_id", columnList = "actor_user_id"),
                @Index(name = "idx_sensitive_access_subject_id", columnList = "subject_id"),
                @Index(name = "idx_sensitive_access_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_sensitive_access_resource", columnList = "resource"),
                @Index(name = "idx_sensitive_access_timestamp", columnList = "timestamp,id"),
                @Index(name = "idx_sensitive_access_correlation_id", columnList = "correlation_id"),
                @Index(name = "ux_sensitive_access_fingerprint", columnList = "event_fingerprint", unique = true)
        }
)
public class SensitiveAccessAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    /* =========================
       CORE
       ========================= */

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(updatable = false, name = "actor_user_id")
    private UUID actorUserId; // optional by design

    @Column(updatable = false, name = "actor_external_subject_id", length = 128)
    private String actorExternalSubjectId; // optional

    @Column(updatable = false, name = "tenant_id")
    private UUID tenantId; // optional depending on context

    /* =========================
       SUBJECT / RESOURCE
       ========================= */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "subject_type", length = 64)
    private SensitiveAccessSubjectType subjectType;

    @Column(nullable = false, updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @Column(nullable = false, updatable = false, length = 128)
    private String resource;

    @Column(nullable = false, updatable = false, length = 64)
    private String action;

    @Column(updatable = false, name = "resource_path", length = 512)
    private String resourcePath; // optional

    /* =========================
       REQUEST CONTEXT
       ========================= */

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "correlation_source", length = 32)
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "execution_context", length = 32)
    private ExecutionContext executionContext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "result", length = 16)
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
    private String reasonDetail; // optional

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "data_classification", length = 32)
    private SensitiveDataClassification dataClassification;

    /* =========================
       INTEGRITY
       ========================= */

    @Column(nullable = false, updatable = false, name = "event_fingerprint", length = 128)
    private String eventFingerprint;

    @Column(nullable = false, updatable = false, name = "chain_version")
    private int chainVersion;

    @Column(nullable = false, updatable = false, name = "prev_event_hash", length = 128)
    private String prevEventHash;

    @Column(nullable = false, updatable = false, name = "event_hash", length = 128)
    private String eventHash;

    /* =========================
       STRICT CONSTRUCTOR
       ========================= */

    public SensitiveAccessAuditEvent(
            Instant timestamp,
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
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {

        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");

        this.actorUserId = actorUserId;
        this.actorExternalSubjectId = actorExternalSubjectId;
        this.tenantId = tenantId;

        this.subjectType = Objects.requireNonNull(subjectType, "subjectType must not be null");
        this.subjectId = requireNonBlank(subjectId, "subjectId");
        this.resource = requireNonBlank(resource, "resource");
        this.action = requireNonBlank(action, "action");
        this.resourcePath = resourcePath;

        this.correlationId = requireNonBlank(correlationId, "correlationId");
        this.correlationSource = Objects.requireNonNull(correlationSource, "correlationSource must not be null");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");

        this.ip = requireNonBlank(ip, "ip");
        this.userAgent = requireNonBlank(userAgent, "userAgent");

        this.reasonCode = requireNonBlank(reasonCode, "reasonCode");
        this.reasonDetail = reasonDetail;

        this.dataClassification = Objects.requireNonNull(dataClassification, "dataClassification must not be null");

        this.eventFingerprint = requireNonBlank(eventFingerprint, "eventFingerprint");
        this.prevEventHash = requireNonBlank(prevEventHash, "prevEventHash");
        this.eventHash = requireNonBlank(eventHash, "eventHash");

        if (chainVersion <= 0) {
            throw new IllegalArgumentException("chainVersion must be > 0");
        }

        this.chainVersion = chainVersion;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

}
