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
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "identity_projection_audit_events",
        indexes = {
                @Index(name = "idx_identity_proj_subject", columnList = "subject_id,timestamp,id"),
                @Index(name = "idx_identity_proj_ts_id", columnList = "timestamp,id"),
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
    @Column(nullable = false, updatable = false)
    private UUID id;

    /* =========================
       CORE
       ========================= */

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false, name = "subject_id", length = 128)
    private String subjectId;

    @Column(nullable = false, updatable = false, name = "correlation_id", length = 128)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "execution_context", length = 32)
    private ExecutionContext executionContext;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, name = "correlation_source", length = 32)
    private CorrelationSource correlationSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditResult result;

    @Column(nullable = false, updatable = false, name = "reason_code", length = 64)
    private String reasonCode;

    /* =========================
       INTEGRITY
       ========================= */

    @Column(nullable = false, updatable = false, unique = true, name = "event_fingerprint", length = 128)
    private String eventFingerprint;

    @Column(nullable = false, updatable = false, name = "chain_version")
    private int chainVersion;

    @Column(nullable = false, updatable = false, name = "prev_event_hash", length = 128)
    private String prevEventHash;

    @Column(nullable = false, updatable = false, name = "event_hash", length = 128)
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
            String prevEventHash,
            String eventHash
    ) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.subjectId = requireNonBlank(subjectId, "subjectId");
        this.correlationId = requireNonBlank(correlationId, "correlationId");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext must not be null");
        this.correlationSource = Objects.requireNonNull(correlationSource, "correlationSource must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.reasonCode = requireNonBlank(reasonCode, "reasonCode");
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
