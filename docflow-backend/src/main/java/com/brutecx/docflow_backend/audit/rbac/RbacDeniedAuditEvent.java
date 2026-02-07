package com.brutecx.docflow_backend.audit.rbac;

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
        name = "rbac_denied_audit_events",
        indexes = {
                @Index(name = "idx_rbac_denied_timestamp", columnList = "timestamp"),
                @Index(name = "idx_rbac_denied_subject_id", columnList = "subject_id"),
                @Index(name = "idx_rbac_denied_correlation_id", columnList = "correlation_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rbac_denied_fingerprint",
                        columnNames = {"event_fingerprint"}
                )
        }
)
public class RbacDeniedAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @Column(name = "subject_id", updatable = false, length = 128)
    private String subjectId;

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
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 64)
    private String eventFingerprint;

    public RbacDeniedAuditEvent(
            String correlationId,
            String subjectId,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {
        this.correlationId = correlationId;
        this.subjectId = subjectId;
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
