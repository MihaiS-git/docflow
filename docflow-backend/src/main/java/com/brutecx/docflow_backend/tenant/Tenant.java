package com.brutecx.docflow_backend.tenant;

import com.brutecx.docflow_backend.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
