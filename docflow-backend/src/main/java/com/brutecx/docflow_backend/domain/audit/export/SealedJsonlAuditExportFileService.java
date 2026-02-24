package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.domain.audit.forensic.DigestingForensicExportService;
import com.brutecx.docflow_backend.domain.security.ExportSigningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SealedJsonlAuditExportFileService {

    private final JdbcTemplate jdbcTemplate;
    private final DigestingForensicExportService digestService;
    private final ExportSigningService signingService;
    private final AuditExportSnapshotRepository snapshotRepository;

    private final Path exportDir;

    public SealedJsonlAuditExportFileService(
            JdbcTemplate jdbcTemplate,
            DigestingForensicExportService digestService,
            ExportSigningService signingService,
            AuditExportSnapshotRepository snapshotRepository,
            @Value("${docflow.audit.retention.export-dir:/var/lib/docflow/audit-retention}") String exportDir
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.digestService = digestService;
        this.signingService = signingService;
        this.snapshotRepository = snapshotRepository;
        this.exportDir = Path.of(exportDir);
    }

    public ExportResult exportByIds(
            String streamName,
            String tableName,
            List<UUID> ids,
            UUID systemUserId
    ) {
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("ids required");
        }

        try {
            Files.createDirectories(exportDir);

            Path tmp = Files.createTempFile(exportDir, "retention-", ".jsonl.tmp");

            ExportSigningService.PayloadSigner signer = signingService.beginPayloadSigner();
            Signature signature = signer.signature();

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {

                var ctx = digestService.beginDigestStream(out, signature);

                List<Map<String, Object>> rows =
                        jdbcTemplate.queryForList(
                                "SELECT * FROM " + tableName + " WHERE id = ANY (?) ORDER BY timestamp ASC, id ASC",
                                ids.toArray()
                        );

                if (rows.size() != ids.size()) {
                    throw new IllegalStateException(
                            "Retention export row mismatch expected=" + ids.size() +
                                    " actual=" + rows.size()
                    );
                }

                if (rows.isEmpty()) {
                    throw new IllegalStateException("Retention export returned no rows");
                }

                for (Map<String, Object> row : rows) {
                    digestService.writePayloadJsonl(row, ctx);
                }

                byte[] digestBytes = ctx.finalizePayloadDigest();
                String digestHex = DigestingForensicExportService.hexSha256(digestBytes);

                ExportSigningService.SignatureResult sigResult = signer.finish();

                Instant now = Instant.now();

                AuditExportSnapshot snapshot =
                        new AuditExportSnapshot(
                                streamName,
                                (Instant) rows.get(0).get("timestamp"),
                                (Instant) rows.get(rows.size() - 1).get("timestamp"),
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

                Path finalPath = exportDir.resolve(
                        streamName + "-" + snapshot.getId() + ".jsonl"
                );

                Files.move(
                        tmp,
                        finalPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

                // Re-validate artifact AFTER move:
                // - recompute SHA-256 over payload bytes
                // - verify signature over payload bytes
                validateArtifact(finalPath, digestHex, sigResult.signatureB64(), sigResult.algorithm(), sigResult.keyId());

                return new ExportResult(
                        snapshot.getId(),
                        finalPath,
                        rows.size(),
                        digestHex,
                        sigResult.keyId()
                );
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

        } catch (IOException e) {
            throw new IllegalStateException("Retention export failed", e);
        }
    }

    private void validateArtifact(
            Path file,
            String expectedDigestHex,
            String signatureB64,
            String signatureAlg,
            String keyId
    ) throws IOException, NoSuchAlgorithmException {
        long fileSize = Files.size(file);
        if (fileSize <= 0) {
            throw new IllegalStateException("Retention artifact empty");
        }

        // determine payload length (fileSize minus last line)
        long payloadLen = determinePayloadLength(file);

        MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            long remaining = payloadLen;

            while (remaining > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (r == -1) break;
                md.update(buf, 0, r);
                remaining -= r;
            }

            if (remaining != 0) {
                throw new IllegalStateException("Retention artifact shorter than expected");
            }

            String actualDigestHex =
                    DigestingForensicExportService.hexSha256(md.digest());

            if (!expectedDigestHex.equalsIgnoreCase(actualDigestHex)) {
                throw new IllegalStateException(
                        "Retention artifact digest mismatch expected=" +
                                expectedDigestHex + " actual=" + actualDigestHex
                );
            }
        }

        try (InputStream in = Files.newInputStream(file)) {
            boolean sigOk = signingService.verifyPayloadSignatureStream(
                    in,
                    payloadLen,
                    signatureB64,
                    signatureAlg,
                    keyId
            );

            if (!sigOk) {
                throw new IllegalStateException("Retention artifact signature verification failed");
            }
        }
    }

    private long determinePayloadLength(Path file) throws IOException {
        try (var raf = new java.io.RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length <= 1) {
                throw new IllegalStateException("Invalid JSONL artifact");
            }

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

    private static int lastIndexOf(byte[] a, byte b, int from) {
        for (int i = Math.min(from, a.length - 1); i >= 0; i--) {
            if (a[i] == b) return i;
        }
        return -1;
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