package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.audit.tamper.AuditChainStateRepository;
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
    private final RetentionCheckpointGuard checkpointGuard;
    private final AuditChainStateRepository chainStateRepository;
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
            RetentionCheckpointGuard checkpointGuard,
            AuditChainStateRepository chainStateRepository,
            @Value("${docflow.audit.retention.batch-size:2000}") int batchSize
    ) {
        this.policyRepository = Objects.requireNonNull(policyRepository);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.actorProps = Objects.requireNonNull(actorProps);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(txManager));
        this.exportFileService = Objects.requireNonNull(exportFileService);
        this.checkpointGuard = Objects.requireNonNull(checkpointGuard);
        this.chainStateRepository = chainStateRepository;
        this.batchSize = Math.max(batchSize, 100);
    }

    public void enforceAllStreams() {

        Map<String, AuditRetentionPolicy> overrides = new HashMap<>();

        for (AuditRetentionPolicy p : policyRepository.findAll()) {

            if (p == null) continue;

            String s = p.getStreamName();

            if (s == null || s.isBlank()) continue;

            overrides.put(s.trim().toUpperCase(Locale.ROOT), p);
        }

        if (!tryAcquireLock()) {
            log.info("audit_retention skipped: another instance is running");
            return;
        }

        try {

            for (String streamName : AuditRetentionStreamRegistry.STREAM_NAMES) {

                AuditRetentionPolicy override = overrides.get(streamName);

                enforceEffectivePolicy(streamName, override);
            }

        } finally {

            releaseLock();
        }
    }

    private void enforceEffectivePolicy(String streamName, AuditRetentionPolicy override) {

        if (streamName == null || streamName.isBlank()) return;

        String streamKey = streamName.trim().toUpperCase(Locale.ROOT);

        AuditRetentionStreamRegistry.StreamTable t =
                AuditRetentionStreamRegistry.STREAMS.get(streamKey);

        if (t == null) {

            log.warn("audit_retention unknown_stream policy_stream={} normalized_stream={} - skipping",
                    streamName, streamKey);

            return;
        }

        AuditRetentionStreamRegistry.RetentionDefault def =
                AuditRetentionStreamRegistry.defaultFor(streamKey);

        int days = override != null ? override.getRetentionDays() : def.retentionDays();

        boolean archiveEnabled =
                override != null ? override.isArchiveEnabled() : def.archiveEnabled();

        if (days <= 0 || !archiveEnabled) {
            return;
        }

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        long total = 0L;

        while (true) {

            Integer updated = txTemplate.execute(status -> {

                jdbcTemplate.execute("SET LOCAL docflow.retention_mode = 'on'");

                return exportAndDeleteBatch(t, cutoff);
            });

            int u = updated != null ? updated : 0;

            if (u <= 0) break;

            total += u;

            if (u < batchSize) break;
        }

        if (total > 0) {

            txTemplate.executeWithoutResult(status ->
                    chainStateRepository.clearCheckpointsOlderThan(
                            t.streamName(),
                            cutoff,
                            Instant.now()
                    )
            );

            log.info(
                    "audit_retention export_and_delete stream={} table={} deleted_count={} cutoff={}",
                    t.streamName(),
                    t.tableName(),
                    total,
                    cutoff
            );
        }
    }

    private int exportAndDeleteBatch(
            AuditRetentionStreamRegistry.StreamTable t,
            Instant cutoff
    ) {

        boolean safe =
                checkpointGuard.isDeletionSafe(
                        t.tableName(),
                        t.timestampColumn(),
                        cutoff
                );

        if (!safe) {

            log.warn(
                    "audit_retention checkpoint_protection_blocked stream={} table={} cutoff={}",
                    t.streamName(),
                    t.tableName(),
                    cutoff
            );

            return 0;
        }

        final String sql =
                "WITH victim AS ( " +
                        " SELECT e." + t.idColumn() +
                        " FROM " + t.tableName() + " e " +
                        " WHERE e." + t.timestampColumn() + " < ? " +
                        " ORDER BY e." + t.timestampColumn() + " ASC, e." + t.idColumn() + " ASC " +
                        " LIMIT ? " +
                        " ), deleted AS ( " +
                        " DELETE FROM " + t.tableName() + " d " +
                        " USING victim v " +
                        " WHERE d." + t.idColumn() + " = v." + t.idColumn() + " " +
                        " RETURNING d.* " +
                        " ) " +
                        "SELECT * FROM deleted";

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        sql,
                        Timestamp.from(cutoff),
                        batchSize
                );

        if (rows.isEmpty()) {
            return 0;
        }

        UUID systemActorId = actorProps.systemActorUuid();

        SealedJsonlAuditExportFileService.ExportResult result =
                exportFileService.exportRows(
                        t.streamName(),
                        rows,
                        systemActorId
                );

        int deleted = rows.size();

        exportedRowsCounter(t.streamName()).increment(result.rowCount());
        deletedRowsCounter(t.streamName()).increment(deleted);
        batchesCounter(t.streamName()).increment();

        log.info(
                "audit_retention export_and_delete stream={} cutoff={} deletedRowCount={} snapshotId={} digest={} keyId={}",
                t.streamName(),
                cutoff,
                deleted,
                result.snapshotId(),
                result.digestHex(),
                result.keyId()
        );

        return deleted;
    }

    private boolean tryAcquireLock() {

        Boolean ok =
                jdbcTemplate.queryForObject(
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