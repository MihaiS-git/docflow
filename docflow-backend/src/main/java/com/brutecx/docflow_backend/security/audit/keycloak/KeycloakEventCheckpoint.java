package com.brutecx.docflow_backend.security.audit.keycloak;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entity representing a checkpoint for Keycloak events.
 * Used to track the last processed event time.
 * This helps in resuming event processing from the last known point.
 * The entity is designed to have a single row identified by a fixed key.
 */
@Entity
@Table(name = "keycloak_event_checkpoint")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeycloakEventCheckpoint {

    @Id
    @Column(nullable = false, updatable = false)
    private String id; // fixed single-row key

    @Column(nullable = false)
    private long lastEventTimeMs;

    public KeycloakEventCheckpoint(String id, long lastEventTimeMs) {
        this.id = id;
        this.lastEventTimeMs = lastEventTimeMs;
    }

    public void update(long lastEventTimeMs) {
        this.lastEventTimeMs = lastEventTimeMs;
    }
}
