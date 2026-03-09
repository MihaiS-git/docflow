package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.audit.tamper.ChainSegmentAware;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
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
@Table(name = "authentication_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthenticationEvent implements ChainSegmentAware {

    @jakarta.persistence.Id
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
    @Column(name = "authentication_result", nullable = false, updatable = false, length = 32)
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

    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 128)
    private String eventFingerprint;

    @Column(name = "chain_version", nullable = false, updatable = false)
    private int chainVersion;

    @Column(name = "prev_event_hash", nullable = false, updatable = false, length = 128)
    private String prevEventHash;

    @Column(name = "event_hash", nullable = false, updatable = false, length = 128)
    private String eventHash;

    @Column(name = "chain_segment_hash", length = 128, updatable = false)
    private String chainSegmentHash;

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
        this.correlationId = correlationId;
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

        this.chainVersion = chainVersion;
    }

    @Override
    public void setChainSegmentHash(String chainSegmentHash) {
        this.chainSegmentHash = chainSegmentHash;
    }

    private static String requireNonBlank(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException();
        }
        return value;
    }
}