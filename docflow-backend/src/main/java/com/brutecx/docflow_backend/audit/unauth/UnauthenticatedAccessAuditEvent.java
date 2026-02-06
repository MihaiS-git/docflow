package com.brutecx.docflow_backend.audit.unauth;

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
        name = "unauthenticated_access_audit_events",
        indexes = {
                @Index(name = "idx_unauth_access_timestamp", columnList = "timestamp"),
                @Index(name = "idx_unauth_access_request_id", columnList = "request_id"),
                @Index(name = "idx_unauth_access_path", columnList = "path")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_unauth_access_event_fingerprint", columnNames = "event_fingerprint")
        }
)
public class UnauthenticatedAccessAuditEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_id", nullable = true, updatable = false, length = 128)
    private String requestId;

    @NotNull
    @Column(name = "http_method", nullable = false, updatable = false, length = 16)
    private String httpMethod;

    @NotNull
    @Column(name = "path", nullable = false, updatable = false, length = 512)
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

    public UnauthenticatedAccessAuditEvent(
            String requestId,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String eventFingerprint
    ) {
        this.requestId = requestId;
        this.httpMethod = httpMethod;
        this.path = path;
        this.ip = ip;
        this.userAgent = userAgent;
        this.eventFingerprint = eventFingerprint;
    }

    @PrePersist
    void prePersist() {
        this.timestamp = Instant.now();
    }
}
