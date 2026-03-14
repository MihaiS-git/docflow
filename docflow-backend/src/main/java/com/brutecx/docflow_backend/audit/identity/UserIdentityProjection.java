package com.brutecx.docflow_backend.audit.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

@Entity
@Table(name = "user_identity_projection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentityProjection {

    @Id
    @Column(name = "subject_id", nullable = false, updatable = false)
    private String subjectId;

    private String username;
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false, name = "last_synced_at")
    private Instant lastSyncedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "realm_roles", columnDefinition = "text[]", nullable = false)
    private String[] realmRoles = new String[0];

    public UserIdentityProjection(String subjectId, String source) {
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.source = Objects.requireNonNull(source, "source");
        this.lastSyncedAt = Instant.EPOCH;
    }

    public void update(
            String username,
            String email,
            String displayName,
            String[] realmRoles
    ) {
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.realmRoles = realmRoles == null ? new String[0] : Arrays.copyOf(realmRoles, realmRoles.length);
        this.lastSyncedAt = Instant.now();
    }

    public boolean sameIdentitySnapshot(
            String username,
            String email,
            String displayName,
            String[] realmRoles
    ) {
        return Objects.equals(this.username, username)
                && Objects.equals(this.email, email)
                && Objects.equals(this.displayName, displayName)
                && Arrays.equals(this.realmRoles, realmRoles == null ? new String[0] : realmRoles);
    }

    public boolean isFresh(Duration maxAge) {
        return lastSyncedAt.isAfter(Instant.now().minus(maxAge));
    }

    public boolean isInitialized() {
        return !Instant.EPOCH.equals(lastSyncedAt);
    }
}