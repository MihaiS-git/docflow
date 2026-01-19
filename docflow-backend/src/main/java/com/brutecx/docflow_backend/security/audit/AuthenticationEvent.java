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
@RequiredArgsConstructor
public class AuthenticationEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NonNull
    @Column(nullable = false)
    private Instant timestamp;

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

    @NonNull
    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

}
