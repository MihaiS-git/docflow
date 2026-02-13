package com.brutecx.docflow_backend.domain.invite;

import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "invites",
        indexes = {
                @Index(name = "idx_invite_token", columnList = "token")
        }
)
@Getter
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

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_role", updatable = false, length = 32)
    private TenantRole tenantRole;

    @Getter
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = true,
            cascade = CascadeType.REMOVE
    )
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    public static Invite create(String email, UUID tenantId, TenantRole tenantRole) {
        if (tenantRole != null && tenantId == null) {
            throw new IllegalArgumentException("tenantRole cannot be set without tenantId");
        }

        Invite invite = new Invite();
        invite.email = Objects.requireNonNull(email);
        invite.token = TokenGenerator.generate();
        invite.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        invite.status = InviteStatus.PENDING;
        invite.timestamp = Instant.now();

        invite.tenantId = tenantId;

        if (tenantId != null) {
            invite.tenantRole = (tenantRole != null)
                    ? tenantRole
                    : TenantRole.MEMBER;
        }

        return invite;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void markAccepted() {
        this.status = InviteStatus.ACCEPTED;
    }

    public void linkUser(User user) {
        this.user = user;
    }

    @PrePersist
    public void prePersist() {
        this.timestamp = Instant.now();
    }

}
