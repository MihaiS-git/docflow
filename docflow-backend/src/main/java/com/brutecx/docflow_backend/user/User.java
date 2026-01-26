package com.brutecx.docflow_backend.user;

import com.brutecx.docflow_backend.tenant.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_tenant_email", columnNames = {"tenant_id", "email"}),
                @UniqueConstraint(name = "uk_users_external_subject", columnNames = {"external_subject_id"})
        }
)
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @NotNull
    @Column(name = "external_subject_id", nullable = false, length = 128)
    private String externalSubjectId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotNull
    @Column(nullable = false)
    private String email;

    @NotNull
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotNull
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @NotNull
    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "job_title")
    private String jobTitle;

    private String department;

    @Column(name = "business_phone")
    private String businessPhone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Setter
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Setter
    @Column(name = "last_login_ip")
    private String lastLoginIp;

    @Setter
    @Column(name = "last_login_user_agent")
    private String lastLoginUserAgent;

    public User(
            String externalSubjectId,
            String email,
            String firstName,
            String lastName
    ) {
        this.externalSubjectId = Objects.requireNonNull(externalSubjectId);
        this.email = Objects.requireNonNull(email).toLowerCase(Locale.ROOT);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);
        this.status = UserStatus.ACTIVE;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();

        if (displayName == null) {
            displayName = (firstName + " " + lastName).trim();
        }

        if (tenant == null) {
            throw new IllegalStateException("User must belong to a tenant");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void assignToTenant(Tenant tenant) {
        Objects.requireNonNull(tenant, "tenant");
        if (this.tenant != null && !this.tenant.equals(tenant)) {
            throw new IllegalStateException("User already assigned to a tenant");
        }
        this.tenant = tenant;
    }

    // ----------------------------
    // Lifecycle state transitions
    // ----------------------------

    public void lock() {
        if (this.status == UserStatus.DISABLED) {
            throw new IllegalStateException("Disabled user cannot be locked");
        }
        this.status = UserStatus.LOCKED;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

}
