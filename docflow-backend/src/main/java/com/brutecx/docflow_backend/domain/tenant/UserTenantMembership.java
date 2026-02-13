package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "user_tenant_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_membership_user_tenant",
                        columnNames = {"user_id", "tenant_id"}
                )
        },
        indexes = {
                @Index(name = "idx_membership_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_membership_user_id", columnList = "user_id"),
                @Index(
                        name = "idx_membership_tenant_role_status",
                        columnList = "tenant_id, role, status"
                )
        }
)
public class UserTenantMembership {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private TenantRole role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MembershipStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserTenantMembership create(
            User user,
            Tenant tenant,
            TenantRole role
    ) {
        UserTenantMembership m = new UserTenantMembership();
        m.user = Objects.requireNonNull(user, "user");
        m.tenant = Objects.requireNonNull(tenant, "tenant");
        m.role = Objects.requireNonNull(role, "role");
        m.status = MembershipStatus.ACTIVE;
        return m;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changeRole(TenantRole newRole) {
        this.role = Objects.requireNonNull(newRole, "newRole");
    }

    public void suspend() {
        this.status = MembershipStatus.SUSPENDED;
    }

    public void activate() {
        this.status = MembershipStatus.ACTIVE;
    }
}
