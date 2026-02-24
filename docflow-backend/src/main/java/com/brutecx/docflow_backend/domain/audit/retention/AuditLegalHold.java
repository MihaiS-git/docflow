package com.brutecx.docflow_backend.domain.audit.retention;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "audit_legal_holds",
        indexes = {
                @Index(name = "idx_audit_legal_hold_stream_active", columnList = "stream_name,active"),
                @Index(name = "idx_audit_legal_hold_event", columnList = "stream_name,event_id,active"),
                @Index(name = "idx_audit_legal_hold_corr", columnList = "stream_name,correlation_id,active"),
                @Index(name = "idx_audit_legal_hold_case_ref", columnList = "case_reference_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLegalHold {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "stream_name", nullable = false, updatable = false, length = 128)
    private String streamName;

    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @Column(name = "case_reference_id", nullable = false, updatable = false, length = 128)
    private String caseReferenceId;

    @Column(name = "reason", nullable = false, updatable = false, length = 1024)
    private String reason;

    @Column(name = "created_by", nullable = false, updatable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    public AuditLegalHold(
            String streamName,
            UUID eventId,
            String correlationId,
            String caseReferenceId,
            String reason,
            String createdBy
    ) {
        this.streamName = requireNonBlank(streamName, "streamName");
        this.eventId = eventId;
        this.correlationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId.trim()
                : null;
        this.caseReferenceId = requireNonBlank(caseReferenceId, "caseReferenceId");
        this.reason = requireNonBlank(reason, "reason");
        this.createdBy = requireNonBlank(createdBy, "createdBy");
        this.createdAt = Instant.now();
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return v.trim();
    }
}