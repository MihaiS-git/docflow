package com.brutecx.docflow_backend.security.audit;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "authentication_event")
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
    @Column(nullable = false)
    private AuthenticationEventSource source;

    @NotNull
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * Principal identifier.
     * - Keycloak userId (UUID) for successful auth
     * - "UNKNOWN" for failed auth
     */
    @NotNull
    @Column(nullable = false)
    private String username;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthenticationResult result;

    @NotNull
    @Column(nullable = false)
    private String idp;

    @NotNull
    @Column(nullable = false)
    private String ip;

    @NotNull
    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    /**
     * Correlation ID to link related events across services.
     * Can be null if not provided in the request.
     */
    @Column(name = "correlation_id", nullable = true)
    private String correlationId;

    @NotNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true)
    private String eventFingerprint;

    public AuthenticationEvent(
            AuthenticationEventSource source,
            Instant timestamp,
            String username,
            AuthenticationResult result,
            String idp,
            String ip,
            String userAgent,
            String correlationId,
            String eventFingerprint
    ) {
        this.source = source;
        this.timestamp = timestamp;
        this.username = username;
        this.result = result;
        this.idp = idp;
        this.ip = ip;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.eventFingerprint = eventFingerprint;
    }
}
