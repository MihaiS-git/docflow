package com.brutecx.docflow_backend.security.audit;

import jakarta.persistence.*;
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

    @NonNull
    @Column(nullable = false)
    private Instant timestamp;

    /**
     * Principal identifier.
     * - Keycloak userId (UUID) for successful auth
     * - "UNKNOWN" for failed auth
     */
    @NonNull
    @Column(nullable = false)
    private String username;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthenticationResult result;

    @NonNull
    @Column(nullable = false)
    private String idp;

    @NonNull
    @Column(nullable = false)
    private String ip;

    @NonNull
    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    @Column(name = "correlation_id", nullable = true)
    private String correlationId;

    @NonNull
    @Column(name = "event_fingerprint", nullable = false, updatable = false, unique = true)
    private String eventFingerprint;

    public AuthenticationEvent(
            Instant timestamp,
            String username,
            AuthenticationResult result,
            String idp,
            String ip,
            String userAgent,
            String correlationId,
            String eventFingerprint
    ) {
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
