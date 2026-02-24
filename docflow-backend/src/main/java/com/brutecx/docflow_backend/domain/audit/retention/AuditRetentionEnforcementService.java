package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.domain.audit.export.SealedJsonlAuditExportFileService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditRetentionEnforcementService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final long ADVISORY_LOCK_KEY = 592337203685477580L;

    private final AuditRetentionPolicyRepository policyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditRetentionSystemActorProperties actorProps;
    private final MeterRegistry meterRegistry;
    private final SealedJsonlAuditExportFileService exportFileService;
    private final TransactionTemplate txTemplate;
    private final int batchSize;

    private final Map<String, Counter> deletedRowsCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> exportedRowsCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> batchesCounters = new ConcurrentHashMap<>();

    public AuditRetentionEnforcementService(
            AuditRetentionPolicyRepository policyRepository,
            JdbcTemplate jdbcTemplate,
            AuditRetentionSystemActorProperties actorProps,
            MeterRegistry meterRegistry,
            PlatformTransactionManager txManager,
            SealedJsonlAuditExportFileService exportFileService,
            @Value("${docflow.audit.retention.batch-size:2000}") int batchSize
    ) {
        this.policyRepository = Objects.requireNonNull(policyRepository);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.actorProps = Objects.requireNonNull(actorProps);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(txManager));
        this.exportFileService = Objects.requireNonNull(exportFileService);
        this.batchSize = Math.max(batchSize, 100);
    }

    public void enforceAllStreams() {
        List<AuditRetentionPolicy> policies = policyRepository.findAll();
        if (policies.isEmpty()) {
            return;
        }

        if (!tryAcquireLock()) {
            log.info("audit_retention skipped: another instance is running");
            return;
        }

        try {
            for (AuditRetentionPolicy p : policies) {
                enforcePolicy(p);
            }
        } finally {
            releaseLock();
        }
    }

    private void enforcePolicy(AuditRetentionPolicy p) {
        if (p == null) return;
        if (!p.isArchiveEnabled()) return;

        int days = p.getRetentionDays();
        if (days <= 0) return;

        String raw = p.getStreamName();
        if (raw == null || raw.isBlank()) return;

        String streamKey = raw.trim().toUpperCase(Locale.ROOT);

        AuditRetentionStreamRegistry.StreamTable t =
                AuditRetentionStreamRegistry.STREAMS.get(streamKey);

        if (t == null) {
            log.warn("audit_retention unknown_stream policy_stream={} normalized_stream={} - skipping",
                    raw, streamKey);
            return;
        }

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        long total = 0L;
        while (true) {
            int updated = txTemplate.execute(status ->
                    exportAndDeleteBatch(t, cutoff)
            );

            if (updated <= 0) break;

            total += updated;

            if (updated < batchSize) break;
        }

        if (total > 0) {
            log.info("audit_retention export_and_delete stream={} table={} deleted_count={} cutoff={}",
                    t.streamName(), t.tableName(), total, cutoff);
        }
    }

    private int exportAndDeleteBatch(
            AuditRetentionStreamRegistry.StreamTable t,
            Instant cutoff
    ) {

        List<UUID> ids = jdbcTemplate.query(
                "SELECT e." + t.idColumn() +
                        " FROM " + t.tableName() + " e " +
                        " WHERE e." + t.timestampColumn() + " < ? " +
                        " AND NOT EXISTS ( " +
                        "   SELECT 1 FROM audit_legal_holds h " +
                        "   WHERE h.active = TRUE " +
                        "     AND h.stream_name = ? " +
                        "     AND ( " +
                        "       (h.event_id IS NULL AND h.correlation_id IS NULL) " +
                        "       OR (h.event_id = e." + t.idColumn() + ") " +
                        "       OR (h.correlation_id IS NOT NULL " +
                        "           AND e." + t.correlationIdColumn() + " IS NOT NULL " +
                        "           AND h.correlation_id = e." + t.correlationIdColumn() + ") " +
                        "     ) " +
                        " ) " +
                        " ORDER BY e." + t.timestampColumn() + " ASC, e." + t.idColumn() + " ASC " +
                        " LIMIT ?",
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                Timestamp.from(cutoff),
                t.streamName(),
                batchSize
        );

        if (ids.isEmpty()) {
            return 0;
        }

        UUID systemActorId = actorProps.systemActorUuid();

        SealedJsonlAuditExportFileService.ExportResult result =
                exportFileService.exportByIds(
                        t.streamName(),
                        t.tableName(),
                        ids,
                        systemActorId
                );

        final String deleteSql =
                "DELETE FROM " + t.tableName() + " e " +
                        " WHERE e.id = ANY (?::uuid[]) " +
                        " AND NOT EXISTS ( " +
                        "   SELECT 1 FROM audit_legal_holds h " +
                        "   WHERE h.active = TRUE " +
                        "     AND h.stream_name = ? " +
                        "     AND ( " +
                        "       (h.event_id IS NULL AND h.correlation_id IS NULL) " +
                        "       OR (h.event_id = e.id) " +
                        "       OR (h.correlation_id IS NOT NULL " +
                        "           AND e." + t.correlationIdColumn() + " IS NOT NULL " +
                        "           AND h.correlation_id = e." + t.correlationIdColumn() + ") " +
                        "     ) " +
                        " )";

        int deleted = jdbcTemplate.execute((Connection con) -> {
            PreparedStatement ps = con.prepareStatement(deleteSql);
            Array uuidArray = con.createArrayOf("uuid", ids.toArray(new UUID[0]));
            ps.setArray(1, uuidArray);
            ps.setString(2, t.streamName());
            return ps;
        }, PreparedStatement::executeUpdate);

        exportedRowsCounter(t.streamName()).increment(result.rowCount());
        deletedRowsCounter(t.streamName()).increment(deleted);
        batchesCounter(t.streamName()).increment();

        log.info("audit_retention export_and_delete stream={} cutoff={} deletedRowCount={} snapshotId={} digest={} keyId={}",
                t.streamName(), cutoff, deleted,
                result.snapshotId(), result.digestHex(), result.keyId());

        return deleted;
    }

    private boolean tryAcquireLock() {
        Boolean ok = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?)",
                Boolean.class,
                ADVISORY_LOCK_KEY
        );
        return Boolean.TRUE.equals(ok);
    }

    private void releaseLock() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_unlock(?)",
                    Boolean.class,
                    ADVISORY_LOCK_KEY
            );
        } catch (Exception ex) {
            log.warn("audit_retention advisory_unlock_failed: {}", ex.getMessage(), ex);
        }
    }

    private Counter deletedRowsCounter(String stream) {
        return deletedRowsCounters.computeIfAbsent(stream, s ->
                Counter.builder("docflow.audit.retention.deleted_rows")
                        .tag("stream", s)
                        .register(meterRegistry)
        );
    }

    private Counter exportedRowsCounter(String stream) {
        return exportedRowsCounters.computeIfAbsent(stream, s ->
                Counter.builder("docflow.audit.retention.exported_rows")
                        .tag("stream", s)
                        .register(meterRegistry)
        );
    }

    private Counter batchesCounter(String stream) {
        return batchesCounters.computeIfAbsent(stream, s ->
                Counter.builder("docflow.audit.retention.batches")
                        .tag("stream", s)
                        .register(meterRegistry)
        );
    }
}