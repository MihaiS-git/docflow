package com.brutecx.docflow_backend.audit.auth;

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
                @Index(name = "idx_auth_events_request_id", columnList = "request_id")
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

    @Column(name = "request_id", nullable = true, updatable = false, length = 128)
    private String requestId;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true, length = 64)
    private String eventFingerprint;

    public AuthenticationEvent(
            AuthenticationEventSource source,
            Instant timestamp,
            String username,
            AuthenticationResult result,
            String idp,
            String ip,
            String userAgent,
            String requestId,
            String eventFingerprint
    ) {
        this.source = source;
        this.timestamp = timestamp;
        this.username = username;
        this.result = result;
        this.idp = idp;
        this.ip = ip;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.eventFingerprint = eventFingerprint;
    }

    @PrePersist
    private void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }
}
