package com.brutecx.docflow_backend.audit.tamper;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuditChainStateRepository extends JpaRepository<AuditChainState, String> {

    /**
     * Locks the chain state row for the duration of the current transaction.
     * This serializes writers for an existing partition and removes the crash window
     * between audit row insert and chain head advancement.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM AuditChainState s
        WHERE s.stateKey = :stateKey
        """)
    Optional<AuditChainState> findByStateKeyForUpdate(
            @Param("stateKey") String stateKey
    );

    /**
     * CAS update for chain progression.
     * Prevents concurrent writers from corrupting the chain.
     */
    @Modifying
    @Query("""
        UPDATE AuditChainState s
        SET s.lastEventHash = :newHash,
            s.updatedAt = :updatedAt,
            s.eventsSinceCheckpoint = :eventsSinceCheckpoint,
            s.eventCount = :eventCount,
            s.lastCheckpointHash = COALESCE(:checkpointHash, s.lastCheckpointHash),
            s.lastCheckpointAt = COALESCE(:checkpointAt, s.lastCheckpointAt)
        WHERE s.stateKey = :stateKey
          AND s.lastEventHash = :expectedPrevHash
        """)
    int compareAndSetHash(
            @Param("stateKey") String stateKey,
            @Param("expectedPrevHash") String expectedPrevHash,
            @Param("newHash") String newHash,
            @Param("eventsSinceCheckpoint") int eventsSinceCheckpoint,
            @Param("eventCount") long eventCount,
            @Param("checkpointHash") String checkpointHash,
            @Param("checkpointAt") Instant checkpointAt,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Bootstrap insert for first event of a partition.
     */
    @Modifying
    @Query(
            value = """
        INSERT INTO audit_chain_state (
            state_key,
            stream,
            tenant_id,
            last_event_hash,
            last_checkpoint_hash,
            last_checkpoint_at,
            events_since_checkpoint,
            event_count,
            updated_at
        )
        VALUES (
            :stateKey,
            :stream,
            :tenantId,
            :lastEventHash,
            :lastCheckpointHash,
            :lastCheckpointAt,
            :eventsSinceCheckpoint,
            :eventCount,
            :updatedAt
        )
        ON CONFLICT (state_key) DO NOTHING
        """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("stateKey") String stateKey,
            @Param("stream") String stream,
            @Param("tenantId") UUID tenantId,
            @Param("lastEventHash") String lastEventHash,
            @Param("lastCheckpointHash") String lastCheckpointHash,
            @Param("lastCheckpointAt") Instant lastCheckpointAt,
            @Param("eventsSinceCheckpoint") int eventsSinceCheckpoint,
            @Param("eventCount") long eventCount,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Clears stale checkpoints during retention cleanup.
     */
    @Modifying
    @Query("""
        UPDATE AuditChainState s
        SET s.lastCheckpointHash = null,
            s.lastCheckpointAt = null,
            s.eventsSinceCheckpoint = 0,
            s.updatedAt = :updatedAt
        WHERE s.stream = :stream
          AND s.lastCheckpointAt IS NOT NULL
          AND s.lastCheckpointAt < :cutoff
        """)
    void clearCheckpointsOlderThan(
            @Param("stream") String stream,
            @Param("cutoff") Instant cutoff,
            @Param("updatedAt") Instant updatedAt
    );
}