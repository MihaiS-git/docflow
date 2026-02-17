package com.brutecx.docflow_backend.audit.identity;

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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "identity_projection_audit_events",
        indexes = {
                @Index(name = "idx_identity_proj_subject", columnList = "subject_id"),
                @Index(name = "idx_identity_proj_ts", columnList = "timestamp"),
                @Index(name = "idx_identity_proj_corr", columnList = "correlation_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_identity_projection_event_fingerprint",
                        columnNames = "event_fingerprint"
                )
        }
)
public class IdentityProjectionAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private ExecutionContext executionContext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @Column(nullable = false, updatable = false, length = 64)
    private String reasonCode;

    @Column(nullable = false, updatable = false, length = 128)
    private String eventFingerprint;

    @Column(nullable = false, updatable = false)
    private int chainVersion;

    @Column(nullable = false, updatable = false, length = 128)
    private String prevHash;

    @Column(nullable = false, updatable = false, length = 128)
    private String eventHash;

    public IdentityProjectionAuditEvent(
            Instant timestamp,
            String subjectId,
            String correlationId,
            ExecutionContext executionContext,
            CorrelationSource correlationSource,
            AuditResult result,
            String reasonCode,
            String eventFingerprint,
            int chainVersion,
            String prevHash,
            String eventHash
    ) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must be set by writer");
        }

        this.timestamp = timestamp;
        this.subjectId = subjectId;
        this.correlationId = correlationId;
        this.executionContext = executionContext;
        this.correlationSource = correlationSource;
        this.result = result;
        this.reasonCode = reasonCode;
        this.eventFingerprint = eventFingerprint;
        this.chainVersion = chainVersion;
        this.prevHash = prevHash;
        this.eventHash = eventHash;
    }

    @PrePersist
    void prePersist() {
        if (this.timestamp == null) {
            throw new IllegalStateException("timestamp must be provided before persist");
        }
    }
}
