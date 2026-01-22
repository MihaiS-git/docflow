package com.brutecx.docflow_backend.user;

import com.brutecx.docflow_backend.tenant.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

    @NotNull
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

    @Builder.Default
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

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

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();

        if (displayName == null && firstName != null && lastName != null) {
            displayName = firstName + " " + lastName;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

}
