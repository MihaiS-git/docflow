package com.brutecx.docflow_backend.audit.unauth;

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
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "unauthenticated_access_audit_events",
        indexes = {
                @Index(name = "idx_unauth_access_timestamp", columnList = "timestamp,id"),
                @Index(name = "idx_unauth_access_correlation_id", columnList = "correlation_id"),
                @Index(name = "idx_unauth_access_path", columnList = "path")
        }
)
public class UnauthenticatedAccessAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "correlation_source", nullable = false, updatable = false, length = 32)
    private CorrelationSource correlationSource;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_context", nullable = false, updatable = false, length = 32)
    private ExecutionContext executionContext;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @NotNull
    @Column(name = "http_method", nullable = false, updatable = false, length = 16)
    private String httpMethod;

    @NotNull
    @Column(name = "path", nullable = false, updatable = false, length = 512)
    private String path;

    @Column(name = "ip", updatable = false, length = 128)
    private String ip;

    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, length = 64)
    private String eventFingerprint;

    @NotNull
    @Column(name = "chain_version", nullable = false, updatable = false)
    private int chainVersion;

    @NotNull
    @Column(name = "prev_event_hash", nullable = false, updatable = false, length = 64)
    private String prevEventHash;

    @NotNull
    @Column(name = "event_hash", nullable = false, updatable = false, length = 64)
    private String eventHash;

    public UnauthenticatedAccessAuditEvent(
            Instant timestamp,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result,
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
        this.correlationId = correlationId;
        this.correlationSource = Objects.requireNonNull(correlationSource, "correlationSource must not be null");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.httpMethod = requireNonBlank(httpMethod, "httpMethod");
        this.path = requireNonBlank(path, "path");
        this.ip = ip;
        this.userAgent = userAgent;
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
