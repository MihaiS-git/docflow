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

    @Column(name = "retention_days",length = 512)
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

    public void disableBootstrap() {
        this.bootstrapEnabled = false;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

}
