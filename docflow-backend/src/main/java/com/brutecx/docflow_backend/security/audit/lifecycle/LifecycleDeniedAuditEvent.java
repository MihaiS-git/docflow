package com.brutecx.docflow_backend.security.audit.lifecycle;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
        name = "lifecycle_denied_audit_events",
        indexes = {
                @Index(name = "idx_lifecycle_denied_timestamp", columnList = "timestamp"),
                @Index(name = "idx_lifecycle_denied_subject_id", columnList = "subject_id"),
                @Index(name = "idx_lifecycle_denied_request_id", columnList = "request_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lifecycle_denied_request_reason",
                        columnNames = {"request_id", "reason_code", "path"}
                ),
                @UniqueConstraint(
                        name = "uk_lifecycle_denied_event_fingerprint",
                        columnNames = {"event_fingerprint"}
                )
        }
)
public class LifecycleDeniedAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_id", nullable = true, updatable = false, length = 128)
    private String requestId;

    @Column(name = "subject_id", nullable = true, updatable = false, length = 128)
    private String subjectId;

    @NotNull
    @Column(name = "reason_code", nullable = false, updatable = false, length = 64)
    private String reasonCode;

    @Column(name = "http_method", nullable = true, updatable = false, length = 16)
    private String httpMethod;

    @Column(name = "path", nullable = true, updatable = false, length = 512)
    private String path;

    @Column(name = "ip", nullable = true, updatable = false, length = 128)
    private String ip;

    @Column(name = "user_agent", nullable = true, updatable = false, length = 512)
    private String userAgent;

    @NotNull
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 64)
    private String eventFingerprint;

    public LifecycleDeniedAuditEvent(
            String requestId,
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {
        this.requestId = requestId;
        this.subjectId = subjectId;
        this.reasonCode = reasonCode;
        this.httpMethod = httpMethod;
        this.path = path;
        this.ip = ip;
        this.userAgent = userAgent;
        this.eventFingerprint = eventFingerprint;
    }

    @PrePersist
    protected void onCreate() {
        this.timestamp = Instant.now();
    }
}
