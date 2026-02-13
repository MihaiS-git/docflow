package com.brutecx.docflow_backend.domain.user;

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
                @UniqueConstraint(name = "uk_users_email", columnNames = {"email"}),
                @UniqueConstraint(name = "uk_users_external_subject", columnNames = {"external_subject_id"})
        }
)
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "external_subject_id", length = 128)
    private String externalSubjectId;

    @NotNull
    @Column(nullable = false, length = 320)
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

    @Setter
    @Column(name = "job_title")
    private String jobTitle;

    @Setter
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
            String email,
            String firstName,
            String lastName,
            String jobTitle,
            String department
    ) {
        this.externalSubjectId = null;
        this.email = Objects.requireNonNull(email).toLowerCase(Locale.ROOT);
        this.firstName = firstName;
        this.lastName = lastName;
        this.jobTitle = jobTitle;
        this.department = department;
        this.status = UserStatus.LOCKED;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();

        if (displayName == null) {
            displayName = (firstName + " " + lastName).trim();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // bind Keycloak subject exactly once
    public void bindExternalSubjectId(String subject) {
        if (this.externalSubjectId != null) {
            return; // idempotent
        }
        this.externalSubjectId = Objects.requireNonNull(subject);
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
