package com.brutecx.docflow_backend.audit.identity;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "identity_projection_audit_events",
        indexes = {
                @Index(name = "idx_identity_proj_subject", columnList = "subject_id"),
                @Index(name = "idx_identity_proj_ts", columnList = "timestamp")
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

    @PrePersist
    void prePersist() {
        this.timestamp = Instant.now();
    }

    public IdentityProjectionAuditEvent(
            String subjectId,
            ExecutionContext executionContext,
            CorrelationSource correlationSource,
            AuditResult result,
            String reasonCode
    ) {
        this.subjectId = subjectId;
        this.executionContext = executionContext;
        this.correlationSource = correlationSource;
        this.result = result;
        this.reasonCode = reasonCode;
    }
}
