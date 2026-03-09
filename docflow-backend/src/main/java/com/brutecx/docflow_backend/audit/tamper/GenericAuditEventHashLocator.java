package com.brutecx.docflow_backend.audit.tamper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GenericAuditEventHashLocator {

    private final EntityManager em;

    /**
     * Maps audit stream -> table + partition column + partition value type.
     */
    private static final Map<String, PartitionMapping> STREAM_MAPPING = Map.of(

            "ADMIN_ACTIONS",
            new PartitionMapping("admin_audit_events", "tenant_id", PartitionKind.TENANT, PartitionValueType.UUID),

            "AUTH",
            new PartitionMapping("authentication_events", "subject_id", PartitionKind.SUBJECT, PartitionValueType.TEXT),

            "CREDENTIAL",
            new PartitionMapping("credential_lifecycle_audit_events", "subject_external_id", PartitionKind.SUBJECT, PartitionValueType.TEXT),

            "IDENTITY_PROJECTION",
            new PartitionMapping("identity_projection_audit_events", "subject_id", PartitionKind.SUBJECT, PartitionValueType.TEXT),

            "LIFECYCLE_DENIED",
            new PartitionMapping("lifecycle_denied_audit_events", "subject_id", PartitionKind.SUBJECT, PartitionValueType.TEXT),

            "RBAC_DENIED",
            new PartitionMapping("rbac_denied_audit_events", "subject_id", PartitionKind.SUBJECT, PartitionValueType.TEXT),

            "ONBOARDING",
            new PartitionMapping("onboarding_audit_events", "tenant_id", PartitionKind.TENANT, PartitionValueType.UUID),

            "SENSITIVE_ACCESS",
            new PartitionMapping("sensitive_access_audit_events", "tenant_id", PartitionKind.TENANT, PartitionValueType.UUID),

            "UNAUTHENTICATED_ACCESS",
            new PartitionMapping("unauthenticated_access_audit_events", null, PartitionKind.GLOBAL, PartitionValueType.NONE)
    );

    public Optional<String> findLastEventHash(AuditPartition partition) {
        PartitionMapping mapping = requireMapping(partition.stream());

        StringBuilder sql = new StringBuilder("""
                SELECT event_hash
                FROM %s
                """.formatted(mapping.table()));

        appendPartitionWhere(sql, partition, mapping);

        sql.append("""
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """);

        Query query = em.createNativeQuery(sql.toString());
        bindPartition(query, partition, mapping);

        List<?> result = query.getResultList();

        if (result.isEmpty()) {
            return Optional.empty();
        }

        String hash = (String) result.getFirst();
        if (hash == null || hash.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(hash);
    }

    public Optional<String> findEventHashByCursor(
            AuditPartition partition,
            UUID cursorId
    ) {
        if (cursorId == null) {
            return Optional.empty();
        }

        PartitionMapping mapping = requireMapping(partition.stream());

        StringBuilder sql = new StringBuilder("""
                SELECT event_hash
                FROM %s
                """.formatted(mapping.table()));

        boolean hasPartition = mapping.partitionColumn() != null;
        if (hasPartition) {
            requirePartitionValue(partition);
            sql.append(" WHERE ").append(mapping.partitionColumn()).append(" = :partition ");
            sql.append(" AND id = :cursorId ");
        } else {
            sql.append(" WHERE id = :cursorId ");
        }

        Query query = em.createNativeQuery(sql.toString());
        if (hasPartition) {
            bindPartition(query, partition, mapping);
        }
        query.setParameter("cursorId", cursorId);

        List<?> result = query.getResultList();
        if (result.isEmpty()) {
            return Optional.empty();
        }

        String hash = (String) result.getFirst();
        if (hash == null || hash.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(hash);
    }

    public Optional<VerificationCursor> findCheckpointCursor(
            AuditPartition partition,
            Instant checkpointAt,
            String checkpointHash
    ) {
        if (checkpointAt == null || checkpointHash == null || checkpointHash.isBlank()) {
            return Optional.empty();
        }

        PartitionMapping mapping = requireMapping(partition.stream());

        StringBuilder sql = new StringBuilder("""
                SELECT id, timestamp
                FROM %s
                """.formatted(mapping.table()));

        boolean hasPartition = mapping.partitionColumn() != null;
        if (hasPartition) {
            requirePartitionValue(partition);
            sql.append(" WHERE ").append(mapping.partitionColumn()).append(" = :partition ");
            sql.append(" AND event_hash = :checkpointHash ");
            sql.append(" AND timestamp >= :checkpointAt ");
        } else {
            sql.append(" WHERE event_hash = :checkpointHash ");
            sql.append(" AND timestamp >= :checkpointAt ");
        }

        sql.append("""
                ORDER BY timestamp ASC, id ASC
                LIMIT 1
                """);

        Query query = em.createNativeQuery(sql.toString());
        if (hasPartition) {
            bindPartition(query, partition, mapping);
        }
        query.setParameter("checkpointHash", checkpointHash);
        query.setParameter("checkpointAt", Timestamp.from(checkpointAt));

        List<?> rows = query.getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = (Object[]) rows.getFirst();
        UUID id = (UUID) row[0];
        Instant ts = ((Timestamp) row[1]).toInstant();

        return Optional.of(new VerificationCursor(ts, id, checkpointHash));
    }

    public Optional<ChainBreak> findFirstContinuityBreak(
            AuditPartition partition,
            String expectedPrevHash,
            Instant cursorTimestamp,
            UUID cursorId,
            int limit
    ) {
        PartitionMapping mapping = requireMapping(partition.stream());

        StringBuilder sql = new StringBuilder("""
                WITH ordered_events AS (
                    SELECT
                        id,
                        timestamp,
                        prev_hash,
                        event_hash,
                        LAG(event_hash) OVER (ORDER BY timestamp ASC, id ASC) AS prior_event_hash,
                        ROW_NUMBER() OVER (ORDER BY timestamp ASC, id ASC) AS rn
                    FROM %s
                """.formatted(mapping.table()));

        bindPartitionAndCursorWhere(sql, partition, mapping, cursorTimestamp, cursorId);
        sql.append("""
                )
                SELECT id, timestamp, prev_hash,
                       CASE
                           WHEN rn = 1 THEN :expectedPrevHash
                           ELSE prior_event_hash
                       END AS expected_prev_hash
                FROM ordered_events
                WHERE (
                    rn = 1 AND COALESCE(prev_hash, '-') <> COALESCE(:expectedPrevHash, '-')
                ) OR (
                    rn > 1 AND COALESCE(prev_hash, '-') <> COALESCE(prior_event_hash, '-')
                )
                ORDER BY timestamp ASC, id ASC
                LIMIT :limit
                """);

        Query query = em.createNativeQuery(sql.toString());
        bindPartitionAndCursor(query, partition, mapping, cursorTimestamp, cursorId);
        query.setParameter("expectedPrevHash", normalizeHash(expectedPrevHash));
        query.setParameter("limit", Math.max(limit, 1));

        List<?> rows = query.getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = (Object[]) rows.getFirst();
        UUID id = (UUID) row[0];
        Instant ts = ((Timestamp) row[1]).toInstant();
        String actualPrevHash = (String) row[2];
        String expected = (String) row[3];

        return Optional.of(new ChainBreak(
                id,
                ts,
                normalizeHash(actualPrevHash),
                normalizeHash(expected)
        ));
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> scanPartition(
            AuditPartition partition,
            Instant cursorTimestamp,
            UUID cursorId,
            int limit
    ) {
        PartitionMapping mapping = requireMapping(partition.stream());

        StringBuilder sql = new StringBuilder("""
                SELECT id, prev_hash, event_hash, timestamp
                FROM %s
                """.formatted(mapping.table()));

        bindPartitionAndCursorWhere(sql, partition, mapping, cursorTimestamp, cursorId);

        sql.append("""
                ORDER BY timestamp ASC, id ASC
                LIMIT :limit
                """);

        Query query = em.createNativeQuery(sql.toString());
        bindPartitionAndCursor(query, partition, mapping, cursorTimestamp, cursorId);
        query.setParameter("limit", limit);

        return query.getResultList();
    }

    public List<AuditPartition> listPartitionsForVerification() {
        List<AuditPartition> partitions = new ArrayList<>();

        for (Map.Entry<String, PartitionMapping> entry : STREAM_MAPPING.entrySet()) {
            String stream = entry.getKey();
            PartitionMapping mapping = entry.getValue();

            switch (mapping.kind()) {
                case GLOBAL -> partitions.add(AuditPartition.global(stream));

                case TENANT, SUBJECT -> {
                    String sql = """
                            SELECT DISTINCT %s
                            FROM %s
                            WHERE %s IS NOT NULL
                            ORDER BY %s ASC
                            """.formatted(
                            mapping.partitionColumn(),
                            mapping.table(),
                            mapping.partitionColumn(),
                            mapping.partitionColumn()
                    );

                    List<?> values = em.createNativeQuery(sql).getResultList();
                    for (Object value : values) {
                        if (value == null) {
                            continue;
                        }

                        String partitionValue = value.toString();
                        if (partitionValue.isBlank()) {
                            continue;
                        }

                        partitions.add(switch (mapping.kind()) {
                            case TENANT -> AuditPartition.tenant(stream, partitionValue);
                            case SUBJECT -> AuditPartition.subject(stream, partitionValue);
                            case GLOBAL -> throw new IllegalStateException("Unexpected GLOBAL branch");
                        });
                    }
                }
            }
        }

        return partitions;
    }

    private PartitionMapping requireMapping(String stream) {
        PartitionMapping mapping = STREAM_MAPPING.get(stream);
        if (mapping == null) {
            throw new IllegalStateException("No audit mapping configured for stream " + stream);
        }
        return mapping;
    }

    private void appendPartitionWhere(
            StringBuilder sql,
            AuditPartition partition,
            PartitionMapping mapping
    ) {
        if (mapping.partitionColumn() != null) {
            requirePartitionValue(partition);
            sql.append(" WHERE ").append(mapping.partitionColumn()).append(" = :partition ");
        }
    }

    private void bindPartition(
            Query query,
            AuditPartition partition,
            PartitionMapping mapping
    ) {
        if (mapping.partitionColumn() == null) {
            return;
        }

        String partitionValue = extractPartitionValue(requirePartitionValue(partition));

        switch (mapping.valueType()) {
            case UUID -> query.setParameter("partition", UUID.fromString(partitionValue));
            case TEXT -> query.setParameter("partition", partitionValue);
            case NONE -> {
                // no-op
            }
        }
    }

    private String extractPartitionValue(String raw) {
        int idx = raw.indexOf(':');
        if (idx < 0) {
            return raw;
        }
        return raw.substring(idx + 1);
    }

    private void bindPartitionAndCursorWhere(
            StringBuilder sql,
            AuditPartition partition,
            PartitionMapping mapping,
            Instant cursorTimestamp,
            UUID cursorId
    ) {
        boolean hasPartition = mapping.partitionColumn() != null;
        boolean hasCursorTs = cursorTimestamp != null;
        boolean hasCursorId = cursorId != null;

        if (hasPartition) {
            requirePartitionValue(partition);
            sql.append(" WHERE ").append(mapping.partitionColumn()).append(" = :partition ");
        }

        if (hasCursorTs) {
            sql.append(hasPartition ? " AND " : " WHERE ");
            if (hasCursorId) {
                sql.append("""
                        (
                            timestamp > :cursorTs
                            OR (timestamp = :cursorTs AND id > :cursorId)
                        )
                        """);
            } else {
                sql.append(" timestamp > :cursorTs ");
            }
        }
    }

    private void bindPartitionAndCursor(
            Query query,
            AuditPartition partition,
            PartitionMapping mapping,
            Instant cursorTimestamp,
            UUID cursorId
    ) {
        bindPartition(query, partition, mapping);

        if (cursorTimestamp != null) {
            query.setParameter("cursorTs", Timestamp.from(cursorTimestamp));
        }
        if (cursorId != null) {
            query.setParameter("cursorId", cursorId);
        }
    }

    private String requirePartitionValue(AuditPartition partition) {
        String partitionValue = partition.partitionValue();
        if (partitionValue == null || partitionValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Partition value required for stream " + partition.stream()
            );
        }
        return partitionValue;
    }

    private String normalizeHash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    public record VerificationCursor(
            Instant timestamp,
            UUID id,
            String eventHash
    ) {
    }

    public record ChainBreak(
            UUID id,
            Instant timestamp,
            String actualPrevHash,
            String expectedPrevHash
    ) {
    }

    private enum PartitionKind {
        GLOBAL,
        TENANT,
        SUBJECT
    }

    private enum PartitionValueType {
        NONE,
        TEXT,
        UUID
    }

    private record PartitionMapping(
            String table,
            String partitionColumn,
            PartitionKind kind,
            PartitionValueType valueType
    ) {
    }

    public boolean verifyTailContinuity(
            AuditPartition partition,
            Instant cursorTimestamp,
            UUID cursorId,
            String expectedPrevHash
    ) {
        PartitionMapping mapping = requireMapping(partition.stream());

        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM %1$s e
                LEFT JOIN %1$s prev
                  ON prev.event_hash = e.prev_hash
                 AND prev.timestamp <= e.timestamp
                """.formatted(mapping.table()));

        bindPartitionAndCursorWhere(sql, partition, mapping, cursorTimestamp, cursorId);

        sql.append("""
                WHERE
                    (
                        e.prev_hash IS NOT NULL
                        AND e.prev_hash <> '-'
                        AND prev.event_hash IS NULL
                    )
                OR
                    (
                        e.prev_hash IS NULL
                        AND COALESCE(:expectedPrevHash,'-') <> '-'
                    )
                """);

        Query query = em.createNativeQuery(sql.toString());
        bindPartitionAndCursor(query, partition, mapping, cursorTimestamp, cursorId);
        query.setParameter("expectedPrevHash", normalizeHash(expectedPrevHash));

        Number result = (Number) query.getSingleResult();

        return result.longValue() == 0;
    }

    public Optional<VerificationCursor> findLastSegmentCursor(
            AuditPartition partition,
            Instant afterTimestamp
    ) {
        PartitionMapping mapping = requireMapping(partition.stream());

        StringBuilder sql = new StringBuilder("""
                SELECT id, timestamp, chain_segment_hash
                FROM %s
                """.formatted(mapping.table()));

        boolean hasPartition = mapping.partitionColumn() != null;

        if (hasPartition) {
            requirePartitionValue(partition);
            sql.append(" WHERE ").append(mapping.partitionColumn()).append(" = :partition ");
            sql.append(" AND chain_segment_hash IS NOT NULL ");
            sql.append(" AND timestamp >= :afterTs ");
        } else {
            sql.append(" WHERE chain_segment_hash IS NOT NULL ");
            sql.append(" AND timestamp >= :afterTs ");
        }

        sql.append("""
                ORDER BY timestamp DESC, id DESC
                LIMIT 1
                """);

        Query query = em.createNativeQuery(sql.toString());

        if (hasPartition) {
            bindPartition(query, partition, mapping);
        }

        query.setParameter("afterTs", Timestamp.from(afterTimestamp));

        List<?> rows = query.getResultList();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Object[] row = (Object[]) rows.get(0);

        return Optional.of(
                new VerificationCursor(
                        ((Timestamp) row[1]).toInstant(),
                        (UUID) row[0],
                        (String) row[2]
                )
        );
    }
}