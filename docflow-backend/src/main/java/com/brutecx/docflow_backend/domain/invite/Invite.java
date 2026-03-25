package com.brutecx.docflow_backend.domain.invite;

import com.brutecx.docflow_backend.domain.tenant.TenantRole;
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

    @Column(name = "hashed_token", nullable = false, unique = true, length = 64)
    private String hashedToken;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "department")
    private String department;

    @Column(nullable = false, name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InviteStatus status;

    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_role", length = 32)
    private TenantRole tenantRole;

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
        Invite invite = new Invite();

        invite.email = normalizeEmail(email);
        invite.firstName = requireNonBlank(firstName, "firstName");
        invite.lastName = requireNonBlank(lastName, "lastName");
        invite.jobTitle = normalizeOptional(jobTitle, 255);
        invite.department = normalizeOptional(department, 255);

        String rawToken = TokenGenerator.generate();
        invite.hashedToken = sha256Hex(rawToken);
        invite.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        invite.status = InviteStatus.PENDING;

        invite.tenantId = tenantId;
        invite.tenantRole = tenantRole != null ? tenantRole : TenantRole.MEMBER;

        return new CreatedInvite(invite, rawToken);
    }

    public String resetForResend(
            String firstName,
            String lastName,
            String jobTitle,
            String department,
            TenantRole tenantRole
    ) {
        if (!isTerminal()) {
            throw new IllegalStateException("Only terminal invites can be reset");
        }

        this.firstName = requireNonBlank(firstName, "firstName");
        this.lastName = requireNonBlank(lastName, "lastName");
        this.jobTitle = normalizeOptional(jobTitle, 255);
        this.department = normalizeOptional(department, 255);

        String rawToken = TokenGenerator.generate();
        this.hashedToken = sha256Hex(rawToken);

        this.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        this.status = InviteStatus.PENDING;
        this.tenantRole = tenantRole != null ? tenantRole : TenantRole.MEMBER;

        return rawToken;
    }

    // ======================
    // STATE TRANSITIONS
    // ======================
    public void markActivated() {
        if (this.status != InviteStatus.PENDING) {
            throw new IllegalStateException("Only PENDING invites can be activated");
        }
        this.status = InviteStatus.ACTIVATED;
    }

    public void markAccepted() {
        if (this.status != InviteStatus.ACTIVATED) {
            throw new IllegalStateException("Only ACTIVATED invites can be accepted");
        }
        this.status = InviteStatus.ACCEPTED;
    }

    public void markRevoked() {
        if (isTerminal()) {
            throw new IllegalStateException("Cannot revoke terminal invite");
        }
        this.status = InviteStatus.REVOKED;
    }

    public void markExpired() {
        if (isTerminal()) {
            throw new IllegalStateException("Cannot expire terminal invite");
        }
        this.status = InviteStatus.EXPIRED;
    }

    // ======================
    // STATE HELPERS
    // ======================
    public boolean isPending() {
        return this.status == InviteStatus.PENDING;
    }

    public boolean isActivated() {
        return this.status == InviteStatus.ACTIVATED;
    }

    public boolean isAccepted() {
        return this.status == InviteStatus.ACCEPTED;
    }

    public boolean isTerminal() {
        return this.status == InviteStatus.ACCEPTED
                || this.status == InviteStatus.REVOKED
                || this.status == InviteStatus.EXPIRED;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }



    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    private static String normalizeEmail(String value) {
        Objects.requireNonNull(value, "email");
        String v = value.trim().toLowerCase();
        if (v.isEmpty()) throw new IllegalArgumentException("email must not be blank");
        if (v.length() > 254) throw new IllegalArgumentException("email too long");
        return v;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String v = value.trim();
        if (v.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (v.length() > 255) throw new IllegalArgumentException(field + " too long");
        return v;
    }

    private static String normalizeOptional(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new IllegalArgumentException("value too long");
        return v;
    }
}