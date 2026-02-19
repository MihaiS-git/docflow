package com.brutecx.docflow_backend.audit.lifecycle;

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
        name = "lifecycle_denied_audit_events",
        indexes = {
                @Index(name = "idx_lifecycle_denied_ts_id", columnList = "timestamp,id"),
                @Index(name = "idx_lifecycle_denied_subject_ts_id", columnList = "subject_id,timestamp,id"),
                @Index(name = "idx_lifecycle_denied_correlation_id", columnList = "correlation_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lifecycle_denied_event_fingerprint",
                        columnNames = "event_fingerprint"
                )
        }
)
public class LifecycleDeniedAuditEvent {

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

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "correlation_source", nullable = false, updatable = false, length = 32)
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_context", nullable = false, updatable = false, length = 32)
    private ExecutionContext executionContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @Column(name = "subject_id", nullable = false, updatable = false, length = 128)
    private String subjectId;

    @Column(name = "reason_code", nullable = false, updatable = false, length = 64)
    private String reasonCode;

    @Column(name = "http_method", nullable = false, updatable = false, length = 16)
    private String httpMethod;

    @Column(name = "path", nullable = false, updatable = false, length = 512)
    private String path;

    @Column(name = "ip", nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(name = "user_agent", nullable = false, updatable = false, length = 512)
    private String userAgent;

    /* =========================
       INTEGRITY
       ========================= */

    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 64)
    private String eventFingerprint;

    @Column(name = "chain_version", nullable = false, updatable = false)
    private int chainVersion;

    @Column(name = "prev_event_hash", nullable = false, updatable = false, length = 128)
    private String prevEventHash;

    @Column(name = "event_hash", nullable = false, updatable = false, length = 128)
    private String eventHash;

    /* =========================
       STRICT CONSTRUCTOR
       ========================= */

    public LifecycleDeniedAuditEvent(
            Instant timestamp,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result,
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {

        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");

        this.correlationId = requireNonBlank(correlationId, "correlationId");
        this.correlationSource = Objects.requireNonNull(correlationSource, "correlationSource must not be null");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");

        this.subjectId = requireNonBlank(subjectId, "subjectId");
        this.reasonCode = requireNonBlank(reasonCode, "reasonCode");
        this.httpMethod = requireNonBlank(httpMethod, "httpMethod");
        this.path = requireNonBlank(path, "path");
        this.ip = requireNonBlank(ip, "ip");
        this.userAgent = requireNonBlank(userAgent, "userAgent");

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
