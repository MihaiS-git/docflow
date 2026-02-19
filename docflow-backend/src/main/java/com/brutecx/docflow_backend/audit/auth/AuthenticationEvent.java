package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
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
@Table(
        name = "authentication_events",
        indexes = {
                @Index(name = "idx_auth_events_ts_id", columnList = "timestamp,id"),
                @Index(name = "idx_auth_events_username_ts_id", columnList = "username,timestamp,id"),
                @Index(name = "idx_auth_events_subject_ts_id", columnList = "subject_id,timestamp,id"),
                @Index(name = "idx_auth_events_correlation_id", columnList = "correlation_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_events_event_fingerprint", columnNames = {"event_fingerprint"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthenticationEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private AuthenticationEventSource source;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false, length = 128)
    private String username;

    @Column(name = "subject_id", nullable = false, updatable = false, length = 128)
    private String subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private AuthenticationResult authenticationResult;

    @Column(nullable = false, updatable = false, length = 64)
    private String idp;

    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @Column(name = "user_agent", nullable = false, updatable = false, length = 512)
    private String userAgent;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "correlation_source", nullable = false, updatable = false, length = 32)
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_context", nullable = false, updatable = false, length = 32)
    private ExecutionContext executionContext;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_result", nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private AuthenticationAuditMetadata metadata;

    @Column(nullable = false, updatable = false, unique = true)
    private String eventFingerprint;

    @Column(nullable = false, updatable = false)
    private int chainVersion;

    @Column(nullable = false, updatable = false)
    private String prevEventHash;

    @Column(nullable = false, updatable = false)
    private String eventHash;

    public AuthenticationEvent(
            AuthenticationEventSource source,
            Instant timestamp,
            String username,
            String subjectId,
            AuthenticationResult authenticationResult,
            String idp,
            String ip,
            String userAgent,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult result,
            AuthenticationAuditMetadata metadata,
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {
        this.source = Objects.requireNonNull(source);
        this.timestamp = Objects.requireNonNull(timestamp);
        this.username = requireNonBlank(username);
        this.subjectId = requireNonBlank(subjectId);
        this.authenticationResult = Objects.requireNonNull(authenticationResult);
        this.idp = requireNonBlank(idp);
        this.ip = requireNonBlank(ip);
        this.userAgent = requireNonBlank(userAgent);
        this.correlationSource = Objects.requireNonNull(correlationSource);
        this.executionContext = Objects.requireNonNull(executionContext);
        this.result = Objects.requireNonNull(result);
        this.metadata = metadata;
        this.eventFingerprint = requireNonBlank(eventFingerprint);
        this.prevEventHash = requireNonBlank(prevEventHash);
        this.eventHash = requireNonBlank(eventHash);

        if (chainVersion <= 0) {
            throw new IllegalArgumentException("chainVersion must be > 0");
        }

        this.correlationId = correlationId;
        this.chainVersion = chainVersion;
    }

    private static String requireNonBlank(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException();
        return v;
    }
}
