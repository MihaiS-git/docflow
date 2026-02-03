package com.brutecx.docflow_backend.domain.invite;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(
        name = "invites",
        indexes = {
                @Index(name = "idx_invite_token", columnList = "token")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invite {

    @Getter
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Getter
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Getter
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Instant expiresAt;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public static Invite create(String email) {
        Invite invite = new Invite();
        invite.email = email;
        invite.token = TokenGenerator.generate();
        invite.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        invite.status = InviteStatus.PENDING;
        invite.timestamp = Instant.now();
        return invite;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markAccepted() {
        this.status = InviteStatus.ACCEPTED;
    }

    public void revoke() {
        this.status = InviteStatus.REVOKED;
    }

    @PrePersist
    public void prePersist() {
        this.timestamp = Instant.now();
    }

}
