package com.brutecx.docflow_backend.audit.auth;

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
import java.util.UUID;

@Entity
@Table(
        name = "authentication_events",
        indexes = {
                @Index(name = "idx_auth_events_timestamp", columnList = "timestamp"),
                @Index(name = "idx_auth_events_username", columnList = "username"),
                @Index(name = "idx_auth_events_correlation_id", columnList = "correlation_id"),
                @Index(
                        name = "idx_auth_events_username_timestamp",
                        columnList = "username, timestamp"
                )
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
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private AuthenticationEventSource source;

    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @NotNull
    @Column(nullable = false, updatable = false, length = 128)
    private String username;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private AuthenticationResult result;

    @NotNull
    @Column(nullable = false, updatable = false, length = 64)
    private String idp;

    @NotNull
    @Column(nullable = false, updatable = false, length = 128)
    private String ip;

    @NotNull
    @Column(name = "user_agent", nullable = false, updatable = false, length = 512)
    private String userAgent;

    @Column(name = "correlation_id", nullable = true, updatable = false, length = 128)
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
    @Column(name = "audit_result", nullable = false, updatable = false, length = 16)
    private AuditResult auditResult;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 64)
    private String eventFingerprint;

    @Column(nullable = false, updatable = false, name = "chain_version")
    private int chainVersion;

    @Column(nullable = false, updatable = false, name = "prev_event_hash", length = 128)
    private String prevEventHash;

    @Column(nullable = false, updatable = false, name = "event_hash", length = 128)
    private String eventHash;

    public AuthenticationEvent(
            AuthenticationEventSource source,
            Instant timestamp,
            String username,
            AuthenticationResult result,
            String idp,
            String ip,
            String userAgent,
            String correlationId,
            CorrelationSource correlationSource,
            ExecutionContext executionContext,
            AuditResult auditResult,
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {
        this.source = source;
        this.timestamp = timestamp;
        this.username = username;
        this.result = result;
        this.idp = idp;
        this.ip = ip;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.correlationSource = correlationSource;
        this.executionContext = executionContext;
        this.auditResult = auditResult;
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
