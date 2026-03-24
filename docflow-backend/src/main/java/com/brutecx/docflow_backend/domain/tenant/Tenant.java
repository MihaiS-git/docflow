package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.error.TenantInvalidArgumentException;
import com.brutecx.docflow_backend.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", foreignKey = @ForeignKey(name = "fk_tenant_owner"))
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_type", nullable = false, length = 32)
    private TenantType tenantType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "data_region", length = 128)
    private String dataRegion;

    @Column(name = "retention_days", nullable = false)
    private Integer retentionDays = 90;

    @Column(name = "bootstrap_enabled", nullable = false)
    private boolean bootstrapEnabled;

    public Tenant(String name) {
        this(name, TenantType.ORGANIZATION, false);
    }

    public static Tenant bootstrapTenant(String name) {
        return new Tenant(name, TenantType.ROOT, true);
    }

    private Tenant(
            String name,
            TenantType type,
            boolean bootstrapEnabled
    ) {
        this.name = canonicalize(name);
        this.status = TenantStatus.ACTIVE;
        this.tenantType = Objects.requireNonNull(type);
        this.bootstrapEnabled = bootstrapEnabled;
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

    private static String canonicalize(String name) {
        Objects.requireNonNull(name);
        String normalized = name.trim().replaceAll("\\s+", " ");
        normalized = java.text.Normalizer.normalize(
                normalized,
                java.text.Normalizer.Form.NFC
        );
        if (normalized.isBlank()) {
            throw new TenantInvalidArgumentException("Tenant name is required");
        }
        if (normalized.length() > 128) {
            throw new TenantInvalidArgumentException("Tenant name too long");
        }
        if (!normalized.matches("^[\\p{L}0-9](?:[\\p{L}0-9 &.,'()\\/+\\-_#]*[\\p{L}0-9])?$")) {
            throw new TenantInvalidArgumentException("Invalid tenant name format");
        }
        return normalized;
    }

    public void updateName(String name) {
        requireActive("UPDATE_NAME");
        this.name = canonicalize(name);
    }

    public void updateDescription(String description) {
        requireActive("UPDATE_DESCRIPTION");
        if (description != null) {
            String normalized = description.trim();
            if (normalized.length() > 1000) {
                throw new TenantInvalidArgumentException("Description too long");
            }
            this.description = normalized.isBlank() ? null : normalized;
        } else {
            this.description = null;
        }
    }

    public void assignOwner(User user) {
        requireActive("ASSIGN_OWNER");
        this.owner = Objects.requireNonNull(user);
    }

    public void updateDataRegion(String dataRegion) {
        requireActive("UPDATE_DATA_REGION");
        if (dataRegion != null) {
            String normalized = dataRegion.trim();
            if (normalized.length() > 128) {
                throw new TenantInvalidArgumentException("Data region too long");
            }
            this.dataRegion = normalized.isBlank() ? null : normalized;
        } else {
            this.dataRegion = null;
        }
    }

    public void updateRetentionDays(Integer retentionDays) {
        requireActive("UPDATE_RETENTION_DAYS");
        if (retentionDays == null) {
            throw new TenantInvalidArgumentException("Retention cannot be null");
        } else if (retentionDays != 0 && retentionDays < 30) {
            throw new TenantInvalidArgumentException("Retention must be >= 30 days or 0 (unlimited)");
        } else if (retentionDays > 3650) {
            throw new TenantInvalidArgumentException("Retention too large (max 3650 days)");
        }
        this.retentionDays = retentionDays;
    }

    public void disableBootstrap() {
        requireActive("DISABLE_BOOTSTRAP");
        this.bootstrapEnabled = false;
    }

    public boolean isRoot() {
        return tenantType == TenantType.ROOT;
    }

    public void suspend() {
        if (status != TenantStatus.ACTIVE) {
            throw new TenantLifecycleViolationException("Only ACTIVE tenant can be suspended");
        }
        status = TenantStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != TenantStatus.SUSPENDED) {
            throw new TenantLifecycleViolationException("Tenant not suspended");
        }
        status = TenantStatus.ACTIVE;
    }

    public void terminate() {
        if (status == TenantStatus.TERMINATED) {
            throw new TenantLifecycleViolationException("Tenant already terminated");
        }
        status = TenantStatus.TERMINATED;
    }

    private void requireActive(String op) {
        if (status != TenantStatus.ACTIVE) {
            throw new TenantLifecycleViolationException(
                    "Tenant must be ACTIVE to perform operation: " + op
            );
        }
    }

    public boolean isOwner(User user) {
        return owner != null && owner.equals(user);
    }
}