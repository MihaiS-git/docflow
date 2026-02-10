package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.domain.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @NotNull
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

    @Column(name = "retention_days", length = 512)
    private Long retentionDays;

    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST, orphanRemoval = true)
    private final List<User> users = new ArrayList<>();

    @Column(name = "bootstrap_enabled", nullable = false)
    private Boolean bootstrapEnabled = true;

    public Tenant(String name) {
        this.name = Objects.requireNonNull(name);
        this.status = TenantStatus.ACTIVE;
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

    public void addUser(User user) {
        Objects.requireNonNull(user);
        user.assignToTenant(this);
        users.add(user);
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public void rename(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public void disableBootstrap() {
        requireActive("DISABLE_BOOTSTRAP");
        this.bootstrapEnabled = false;
    }

    public void suspend() {
        if (this.status == TenantStatus.SUSPENDED) {
            throw new TenantLifecycleViolationException("Tenant is already SUSPENDED");
        }
        this.status = TenantStatus.SUSPENDED;
    }

    public void reactivate() {
        if (this.status != TenantStatus.SUSPENDED) {
            throw new TenantLifecycleViolationException(
                    "Tenant is not SUSPENDED; cannot reactivate"
            );
        }
        this.status = TenantStatus.ACTIVE;
    }

    public void updateName(String name) {
        requireActive("UPDATE_NAME");
        this.name = Objects.requireNonNull(name);
    }

    public void updateDataRegion(String dataRegion) {
        requireActive("UPDATE_DATA_REGION");
        this.dataRegion = dataRegion;
    }

    public void updateRetentionDays(Long retentionDays) {
        requireActive("UPDATE_RETENTION_DAYS");
        this.retentionDays = retentionDays;
    }

    private void requireActive(String operation) {
        if (this.status == TenantStatus.SUSPENDED) {
            throw new TenantLifecycleViolationException("Tenant is SUSPENDED; operation denied: " + operation);
        }
    }

}
