package com.brutecx.docflow_backend.audit.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Entity representing a projection of user identity information.
 * This entity is used to cache user details fetched from an external identity provider.
 * It includes fields for username, email, display name, source of identity,
 * and the timestamp of the last synchronization.
 * Methods are provided to update the identity information and check its freshness.
 */
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

    @Column(nullable = false)
    private Instant lastSyncedAt;

    public UserIdentityProjection(String subjectId, String source) {
        this.subjectId = subjectId;
        this.source = source;
        this.lastSyncedAt = Instant.EPOCH;
    }

    public void update(String username, String email, String displayName) {
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.lastSyncedAt = Instant.now();
    }

    public boolean isFresh(Duration maxAge) {
        return lastSyncedAt.isAfter(Instant.now().minus(maxAge));
    }

    public boolean isInitialized() {
        return !Instant.EPOCH.equals(lastSyncedAt);
    }

}
