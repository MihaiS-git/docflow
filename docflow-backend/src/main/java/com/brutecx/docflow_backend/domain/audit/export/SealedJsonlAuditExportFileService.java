package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.domain.audit.forensic.DigestingForensicExportService;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.rotation.AuditSigningKeyRotationService;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.ExportSigningService;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class SealedJsonlAuditExportFileService {

    private static final Logger log =
            LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String STREAM = "AUDIT_RETENTION_EXPORT";
    private static final String EXEC_CTX = "SYSTEM";

    private static final int META_VERSION = 1;

    private final DigestingForensicExportService digestService;
    private final ExportSigningService signingService;
    private final AuditSigningKeyRotationService rotationService;
    private final AuditExportSnapshotRepository snapshotRepository;
    private final AuditWriteFailureMetrics metrics;

    private final Path exportDir;

    public SealedJsonlAuditExportFileService(
            DigestingForensicExportService digestService,
            ExportSigningService signingService,
            AuditSigningKeyRotationService rotationService,
            AuditExportSnapshotRepository snapshotRepository,
            AuditWriteFailureMetrics metrics,
            @Value("${docflow.audit.retention.export-dir:/var/lib/docflow/audit-retention}")
            String exportDir
    ) {
        this.digestService = digestService;
        this.signingService = signingService;
        this.rotationService = rotationService;
        this.snapshotRepository = snapshotRepository;
        this.metrics = metrics;
        this.exportDir = Path.of(exportDir);
    }

    private ExportResult exportInternal(
            String streamName,
            List<Map<String, Object>> rows,
            UUID systemUserId
    ) {
        final long startNs = System.nanoTime();
        String correlationId = Optional.ofNullable(MDC.get("correlationId"))
                .orElse("retention-" + UUID.randomUUID());

        final long requestedRowCount = (rows == null) ? 0L : rows.size();

        try {
            Objects.requireNonNull(streamName, "streamName");
            Objects.requireNonNull(systemUserId, "systemUserId");

            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("rows required");
            }

            Files.createDirectories(exportDir);
            Path tmp = Files.createTempFile(exportDir, "retention-", ".jsonl.tmp");

            // Rotation happens exactly once here
            AuditSigningKey activeKey = rotationService.requireActiveForExport();

            ExportSigningService.PayloadSigner signer =
                    signingService.beginPayloadSigner(activeKey);

            Signature signature = signer.signature();

            try (OutputStream out =
                         new BufferedOutputStream(Files.newOutputStream(tmp))) {

                var ctx = digestService.beginDigestStream(out, signature);

                for (Map<String, Object> row : rows) {
                    digestService.writePayloadJsonl(row, ctx);
                }

                byte[] digestBytes = ctx.finalizePayloadDigest();
                String digestHex =
                        DigestingForensicExportService.hexSha256(digestBytes);

                ExportSigningService.SignatureResult sigResult =
                        signer.finish();

                Instant now = Instant.now();

                Instant firstTs = (Instant) rows.getFirst().get("timestamp");
                Instant lastTs = (Instant) rows.getLast().get("timestamp");

                AuditExportSnapshot snapshot =
                        new AuditExportSnapshot(
                                streamName,
                                firstTs,
                                lastTs,
                                null,
                                digestHex,
                                rows.size(),
                                now,
                                systemUserId,
                                sigResult.signatureB64(),
                                sigResult.algorithm(),
                                sigResult.keyId()
                        );

                snapshotRepository.saveAndFlush(snapshot);

                AuditExportMetadataDTO meta = new AuditExportMetadataDTO(
                        META_VERSION,
                        streamName,
                        firstTs,
                        lastTs,
                        null,
                        rows.size(),
                        digestHex,
                        sigResult.signatureB64(),
                        sigResult.algorithm(),
                        sigResult.keyId(),
                        activeKey.getFingerprintSha256Hex(),
                        activeKey.getPublicKeyPem(),
                        AuditExportMetadataDTO.DIGEST_ALG_SHA256,
                        AuditExportMetadataDTO.SIGNATURE_INPUT_PAYLOAD_JSONL_BYTES,
                        snapshot.getId(),
                        now
                );

                digestService.writeMetaJsonl(
                        Map.of("_export_meta", meta),
                        ctx
                );

                ctx.flush();

                Path finalPath =
                        exportDir.resolve(streamName + "-" + snapshot.getId() + ".jsonl");

                Files.move(
                        tmp,
                        finalPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

                emitLog(
                        true,
                        correlationId,
                        snapshot.getId(),
                        streamName,
                        rows.size(),
                        digestHex,
                        sigResult.keyId()
                );

                metrics.incrementSuccess(STREAM, EXEC_CTX);
                metrics.recordLatency(STREAM, EXEC_CTX,
                        Duration.ofNanos(System.nanoTime() - startNs));

                return new ExportResult(
                        snapshot.getId(),
                        finalPath,
                        rows.size(),
                        digestHex,
                        sigResult.keyId()
                );
            }
        } catch (Exception e) {
            emitLog(
                    false,
                    correlationId,
                    null,
                    streamName,
                    requestedRowCount,
                    null,
                    null,
                    kv("exception.class", e.getClass().getSimpleName())
            );

            metrics.incrementFailure(STREAM, EXEC_CTX, e);
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));

            throw new IllegalStateException("Retention export failed", e);
        }
    }

    private static void emitLog(
            boolean ok,
            String correlationId,
            UUID snapshotId,
            String stream,
            long rowCount,
            String digestHex,
            String keyId,
            StructuredArgument... extra
    ) {

        StructuredArgument[] base = new StructuredArgument[]{
                kv("event.category", "audit"),
                kv("event.action", "RETENTION_EXPORT"),
                kv("event.outcome", ok ? "success" : "failure"),
                kv("audit.stream", STREAM),
                kv("correlation.id", correlationId),
                kv("snapshot.id", snapshotId),
                kv("snapshot.stream", stream),
                kv("row.count", rowCount),
                kv("digest.sha256", digestHex),
                kv("key.id", keyId)
        };

        StructuredArgument[] args =
                extra != null && extra.length > 0
                        ? Arrays.copyOf(base, base.length + extra.length)
                        : base;

        if (extra != null && extra.length > 0) {
            System.arraycopy(extra, 0, args, base.length, extra.length);
        }

        if (ok) {
            log.info("security_event {}", (Object[]) args);
        } else {
            log.error("security_event {}", (Object[]) args);
        }
    }

    public record ExportResult(
            UUID snapshotId,
            Path filePath,
            long rowCount,
            String digestHex,
            String keyId
    ) {
    }

    /**
     * Export rows already fetched by caller.
     * Intended for retention enforcement using DELETE ... RETURNING (avoids re-reading rows).
     * Contract:
     * - rows must include the "timestamp" column (Instant) used for snapshot bounds.
     * - rows should be ordered by timestamp ASC, id ASC for determinism.
     */
    public ExportResult exportRows(
            String streamName,
            List<Map<String, Object>> rows,
            UUID systemUserId
    ) {
        return exportInternal(streamName, rows, systemUserId);
    }

}