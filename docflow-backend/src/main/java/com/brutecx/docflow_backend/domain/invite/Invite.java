package com.brutecx.docflow_backend.domain.invite;

import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.brutecx.docflow_backend.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import static com.brutecx.docflow_backend.infrastructure.security.HashUtils.sha256Hex;

@Entity
@Table(
        name = "invites",
        indexes = {
                @Index(name = "idx_invite_tenant_created_at", columnList = "tenant_id, created_at"),
                @Index(name = "idx_invite_tenant_status_created", columnList = "tenant_id, status, created_at"),
                @Index(name = "idx_invite_tenant_status_expires", columnList = "tenant_id, status, expires_at"),
                @Index(name = "idx_invite_tenant_email", columnList = "tenant_id, email")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invite {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "hashed_token", updatable = false, nullable = false, unique = true, length = 64)
    private String hashedToken;

    @Column(nullable = false, length = 254)
    private String email;

    /* identity snapshot from invite request */

    @Column(name = "first_name", nullable = false, length = 255)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 255)
    private String lastName;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Column(name = "department", length = 255)
    private String department;

    @Column(nullable = false, name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status;

    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_role", updatable = false, length = 32)
    private TenantRole tenantRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;

    public static CreatedInvite create(
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String department,
            UUID tenantId,
            TenantRole tenantRole
    ) {
        if (tenantRole != null && tenantId == null) {
            throw new IllegalArgumentException("tenantRole cannot be set without tenantId");
        }

        Invite invite = new Invite();

        invite.email = requireValidEmail(email);
        invite.firstName = requireNonBlank(firstName, "firstName");
        invite.lastName = requireNonBlank(lastName, "lastName");
        invite.jobTitle = normalizeOptional(jobTitle, 255);
        invite.department = normalizeOptional(department, 255);

        String rawToken = TokenGenerator.generate();
        invite.hashedToken = sha256Hex(rawToken);
        invite.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        invite.status = InviteStatus.PENDING;

        invite.tenantId = tenantId;

        if (tenantId != null) {
            invite.tenantRole = (tenantRole != null)
                    ? tenantRole
                    : TenantRole.MEMBER;
        }

        return new CreatedInvite(invite, rawToken);
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
        this.createdAt = Instant.now();
    }

    private static String requireValidEmail(String value) {
        Objects.requireNonNull(value, "email");
        String v = value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (v.length() > 254) {
            throw new IllegalArgumentException("email too long");
        }
        return v;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String v = value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (v.length() > 255) {
            throw new IllegalArgumentException(field + " too long");
        }
        return v;
    }

    private static String normalizeOptional(String value, int max) {
        if (value == null) return null;

        String v = value.trim();
        if (v.isEmpty()) return null;

        if (v.length() > max) {
            throw new IllegalArgumentException("value too long");
        }

        return v;
    }
}