package com.brutecx.docflow_backend.tenant;

import com.brutecx.docflow_backend.user.User;
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

    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST, orphanRemoval = true)
    private final List<User> users = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Tenant(String name) {
        this.name = Objects.requireNonNull(name);
        this.status = TenantStatus.ACTIVE;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public void addUser(User user) {
        Objects.requireNonNull(user);
        user.assignToTenant(this);
        users.add(user);
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }
}
