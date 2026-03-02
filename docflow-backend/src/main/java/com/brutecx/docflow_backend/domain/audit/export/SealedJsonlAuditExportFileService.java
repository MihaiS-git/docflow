package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.domain.audit.forensic.DigestingForensicExportService;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.ExportSigningService;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class SealedJsonlAuditExportFileService {

    private static final Logger log =
            LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String STREAM = "AUDIT_RETENTION_EXPORT";
    private static final String EXEC_CTX = "SYSTEM";

    private final JdbcTemplate jdbcTemplate;
    private final DigestingForensicExportService digestService;
    private final ExportSigningService signingService;
    private final AuditExportSnapshotRepository snapshotRepository;
    private final AuditWriteFailureMetrics metrics;

    private final Path exportDir;

    public SealedJsonlAuditExportFileService(
            JdbcTemplate jdbcTemplate,
            DigestingForensicExportService digestService,
            ExportSigningService signingService,
            AuditExportSnapshotRepository snapshotRepository,
            AuditWriteFailureMetrics metrics,
            @Value("${docflow.audit.retention.export-dir:/var/lib/docflow/audit-retention}")
            String exportDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.digestService = digestService;
        this.signingService = signingService;
        this.snapshotRepository = snapshotRepository;
        this.metrics = metrics;
        this.exportDir = Path.of(exportDir);
    }

    public ExportResult exportByIds(
            String streamName,
            String tableName,
            List<UUID> ids,
            UUID systemUserId
    ) {

        final long startNs = System.nanoTime();
        String correlationId = Optional.ofNullable(MDC.get("correlationId"))
                .orElse("retention-" + UUID.randomUUID());

        try {

            if (ids.isEmpty()) {
                throw new IllegalArgumentException("ids required");
            }

            Files.createDirectories(exportDir);
            Path tmp = Files.createTempFile(exportDir, "retention-", ".jsonl.tmp");

            ExportSigningService.PayloadSigner signer =
                    signingService.beginPayloadSigner();

            Signature signature = signer.signature();

            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            "SELECT * FROM " + tableName +
                                    " WHERE id = ANY (?) ORDER BY timestamp ASC, id ASC",
                            ids.toArray()
                    );

            if (rows.size() != ids.size()) {
                throw new IllegalStateException("Retention export row mismatch");
            }

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

                AuditExportSnapshot snapshot =
                        new AuditExportSnapshot(
                                streamName,
                                (Instant) rows.getFirst().get("timestamp"),
                                (Instant) rows.getLast().get("timestamp"),
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

                digestService.writeMetaJsonl(
                        Map.of(
                                "_export_meta", true,
                                "snapshotId", snapshot.getId(),
                                "sha256DigestHex", digestHex,
                                "rowCount", rows.size(),
                                "signature", sigResult.signatureB64(),
                                "signatureAlg", sigResult.algorithm(),
                                "keyId", sigResult.keyId()
                        ),
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

                validateArtifact(
                        finalPath,
                        digestHex,
                        sigResult.signatureB64(),
                        sigResult.algorithm(),
                        sigResult.keyId()
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

            emitLog(false, correlationId, null,
                    streamName, ids.size(), null, null,
                    kv("exception.class", e.getClass().getSimpleName()));

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

    private void validateArtifact(
            Path file,
            String expectedDigestHex,
            String signatureB64,
            String signatureAlg,
            String keyId
    ) throws IOException, NoSuchAlgorithmException {

        long payloadLen = determinePayloadLength(file);

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            long remaining = payloadLen;
            while (remaining > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                md.update(buf, 0, r);
                remaining -= r;
            }
        }

        String actualDigestHex =
                DigestingForensicExportService.hexSha256(md.digest());

        if (!expectedDigestHex.equalsIgnoreCase(actualDigestHex)) {
            throw new IllegalStateException("Retention digest mismatch");
        }

        try (InputStream in = Files.newInputStream(file)) {
            boolean sigOk =
                    signingService.verifyPayloadSignatureStream(
                            in,
                            payloadLen,
                            signatureB64,
                            signatureAlg,
                            keyId
                    );

            if (!sigOk) {
                throw new IllegalStateException("Retention signature invalid");
            }
        }
    }

    private long determinePayloadLength(Path file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            long pos = length - 1;
            int newlineCount = 0;

            while (pos >= 0) {
                raf.seek(pos);
                if (raf.readByte() == '\n') {
                    newlineCount++;
                    if (newlineCount == 2) {
                        return pos + 1;
                    }
                }
                pos--;
            }
            throw new IllegalStateException("Invalid JSONL structure");
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
}