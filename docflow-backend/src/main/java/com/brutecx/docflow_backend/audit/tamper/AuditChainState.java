package com.brutecx.docflow_backend.audit.tamper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_chain_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditChainState {

    @Id
    @Column(name = "state_key", nullable = false, updatable = false, length = 256)
    private String stateKey;

    @Column(name = "stream", nullable = false, updatable = false, length = 128)
    private String stream;

    @Column(name = "tenant_id", updatable = false, length = 64)
    private String tenantId;

    @Column(name = "last_event_hash", nullable = false, length = 128)
    private String lastEventHash;

    @Column(name = "last_checkpoint_hash", length = 128)
    private String lastCheckpointHash;

    @Column(name = "last_checkpoint_at")
    private Instant lastCheckpointAt;

    @Column(name = "events_since_checkpoint", nullable = false)
    private int eventsSinceCheckpoint;

    /**
     * Monotonic counter used for segment anchors.
     */
    @Column(name = "event_count", nullable = false)
    private long eventCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AuditChainState(
            String stateKey,
            String stream,
            String tenantId,
            String lastEventHash
    ) {
        this.stateKey = stateKey;
        this.stream = stream;
        this.tenantId = tenantId;
        this.lastEventHash = lastEventHash;
        this.eventsSinceCheckpoint = 0;
        this.eventCount = 0;
        this.updatedAt = Instant.now();
    }

    public AdvanceResult advance(
            String newEventHash,
            int checkpointInterval,
            Instant now
    ) {
        int nextCheckpointCount = eventsSinceCheckpoint + 1;
        long nextEventCount = eventCount + 1;

        if (nextCheckpointCount >= checkpointInterval) {
            return new AdvanceResult(
                    newEventHash,
                    0,
                    newEventHash,
                    now,
                    nextEventCount
            );
        }

        return new AdvanceResult(
                newEventHash,
                nextCheckpointCount,
                null,
                null,
                nextEventCount
        );
    }

    public boolean hasCheckpoint() {
        return lastCheckpointHash != null
                && !lastCheckpointHash.isBlank()
                && lastCheckpointAt != null;
    }

    public record AdvanceResult(
            String newHash,
            int eventsSinceCheckpoint,
            String checkpointHash,
            Instant checkpointAt,
            long eventCount
    ) {}
}