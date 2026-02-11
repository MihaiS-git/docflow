package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "tenants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tenants_name", columnNames = {"name"})
        }
)
public class Tenant {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @NotBlank
    @Size(max = 128)
    @Column(nullable = false, length = 128)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    @Column(name = "data_region", length = 512)
    private String dataRegion;

    @Column(name = "retention_days")
    private Long retentionDays;

    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST, orphanRemoval = true)
    private final List<User> users = new ArrayList<>();

    /**
     * TRUE only for system bootstrap tenant.
     */
    @Column(name = "bootstrap_enabled", nullable = false)
    private Boolean bootstrapEnabled;

    /* =====================================================
       Constructors / factories
       ===================================================== */

    /**
     * Normal tenant creation (admin/UI).
     * Bootstrap is ALWAYS disabled.
     */
    public Tenant(String name) {
        this(name, false);
    }

    /**
     * Explicit bootstrap tenant factory.
     * Only TenantBootstrap is allowed to call this.
     */
    public static Tenant bootstrapTenant(String name) {
        return new Tenant(name, true);
    }

    private Tenant(String name, boolean bootstrapEnabled) {
        this.name = canonicalize(name);
        this.status = TenantStatus.ACTIVE;
        this.bootstrapEnabled = bootstrapEnabled;
    }

    /* ===================================================== */

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String canonicalize(String name) {
        Objects.requireNonNull(name, "name");
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Tenant name is required");
        }
        return normalized;
    }

    /* ===================================================== */

    public void updateName(String name) {
        requireActive("UPDATE_NAME");
        this.name = canonicalize(name);
    }

    public void updateDataRegion(String dataRegion) {
        requireActive("UPDATE_DATA_REGION");
        this.dataRegion = dataRegion;
    }

    public void updateRetentionDays(Long retentionDays) {
        requireActive("UPDATE_RETENTION_DAYS");
        this.retentionDays = retentionDays;
    }

    public void addUser(User user) {
        Objects.requireNonNull(user);
        user.assignToTenant(this);
        users.add(user);
    }

    public boolean isBootstrapEnabled() {
        return Boolean.TRUE.equals(bootstrapEnabled);
    }

    public void disableBootstrap() {
        requireActive("DISABLE_BOOTSTRAP");
        this.bootstrapEnabled = false;
    }

    public void suspend() {
        if (status == TenantStatus.SUSPENDED) {
            throw new TenantLifecycleViolationException("Tenant already suspended");
        }
        status = TenantStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != TenantStatus.SUSPENDED) {
            throw new TenantLifecycleViolationException("Tenant not suspended");
        }
        status = TenantStatus.ACTIVE;
    }

    private void requireActive(String op) {
        if (status == TenantStatus.SUSPENDED) {
            throw new TenantLifecycleViolationException(
                    "Tenant suspended; operation denied: " + op
            );
        }
    }
}
