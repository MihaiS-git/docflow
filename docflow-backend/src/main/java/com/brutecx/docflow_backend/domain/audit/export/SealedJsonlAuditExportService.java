package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.brutecx.docflow_backend.api.dto.audit.BaseAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.domain.audit.forensic.DigestingForensicExportService;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.rotation.AuditSigningKeyRotationService;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.ExportSigningService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@RequiredArgsConstructor
public class SealedJsonlAuditExportService {

    public static final int DEFAULT_META_VERSION = 1;

    @FunctionalInterface
    public interface CursorBatchFetcher<E> {
        Page<E> fetch(Instant cursorTimestamp, UUID cursorId);
    }

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    /**
     * Operational stream name for SIEM/metrics (NOT the per-audit-table stream).
     */
    private static final String STREAM = "AUDIT_EXPORT_JSONL_SEALED";
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    private final DigestingForensicExportService digestingExportService;
    private final ExportSigningService exportSigningService;
    private final AuditSigningKeyRotationService rotationService;
    private final AuditExportSnapshotRepository auditExportSnapshotRepository;
    private final UserService userService;
    private final AuditWriteFailureMetrics metrics;

    @Transactional
    public <E, T extends BaseAuditForensicExportDTO> void exportSealedJsonl(
            OutputStream out,
            String stream,
            Instant from,
            Instant to,
            UUID tenantId,
            int exportMaxRows,
            CursorBatchFetcher<E> fetcher,
            Function<E, T> mapper,
            Runnable afterSuccess
    ) {

        final long startNs = System.nanoTime();

        if (out == null) throw new IllegalArgumentException("out required");
        if (stream == null || stream.isBlank()) throw new IllegalArgumentException("stream required");
        if (from == null || to == null) throw new IllegalArgumentException("from/to required");
        if (from.isAfter(to)) throw new IllegalArgumentException("Invalid range: from > to");
        if (exportMaxRows <= 0) throw new IllegalArgumentException("exportMaxRows must be > 0");
        if (fetcher == null) throw new IllegalArgumentException("fetcher required");
        if (mapper == null) throw new IllegalArgumentException("mapper required");

        final String correlationId = resolveCorrelationId();
        final CorrelationSource correlationSource = resolveCorrelationSource();

        AuditExportSnapshot snapshot = null;
        long exported = 0L;

        try {
            // Ensure active key exists before writing payload bytes
            AuditSigningKey activeKey = rotationService.requireActiveForExport();

            User actor = userService.getRequiredCurrentUser();

            DigestingForensicExportService.ExportDigestContext ctx;
            ExportSigningService.PayloadSigner signer;

            try {
                // Start signer first; digest stream updates BOTH digest + signature from same bytes.
                signer = exportSigningService.beginPayloadSigner();
                ctx = digestingExportService.beginDigestStream(out, signer.signature());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize export stream", e);
            }

            Instant cursorTimestamp = null;
            UUID cursorId = null;

            while (true) {
                Page<E> batch = fetcher.fetch(cursorTimestamp, cursorId);
                if (batch == null || batch.isEmpty()) break;

                for (E entity : batch.getContent()) {

                    if (exported >= exportMaxRows) {
                        throw new IllegalStateException("Export row limit exceeded: " + exportMaxRows);
                    }

                    T dto = mapper.apply(entity);

                    if (dto == null) throw new IllegalStateException("Mapper returned null DTO");
                    if (dto.timestamp() == null || dto.id() == null) {
                        throw new IllegalStateException("DTO must provide timestamp and id");
                    }

                    digestingExportService.writePayloadJsonl(dto, ctx);

                    exported++;
                    cursorTimestamp = dto.timestamp();
                    cursorId = dto.id();
                }
            }

            byte[] payloadDigestBytes = ctx.finalizePayloadDigest();
            String payloadDigestHex = DigestingForensicExportService.hexSha256(payloadDigestBytes);

            ExportSigningService.SignatureResult sig = signer.finish();

            Instant now = Instant.now();

            snapshot = new AuditExportSnapshot(
                    stream,
                    from,
                    to,
                    tenantId,
                    payloadDigestHex,
                    exported,
                    now,
                    actor.getId(),
                    sig.signatureB64(),
                    sig.algorithm(),
                    sig.keyId()
            );

            auditExportSnapshotRepository.save(snapshot);
            auditExportSnapshotRepository.flush();

            AuditExportMetadataDTO meta = new AuditExportMetadataDTO(
                    DEFAULT_META_VERSION,
                    stream,
                    from,
                    to,
                    tenantId,
                    exported,
                    payloadDigestHex,
                    sig.signatureB64(),
                    sig.algorithm(),
                    sig.keyId(),
                    activeKey.getFingerprintSha256Hex(),
                    AuditExportMetadataDTO.DIGEST_ALG_SHA256,
                    AuditExportMetadataDTO.SIGNATURE_INPUT_PAYLOAD_JSONL_BYTES,
                    snapshot.getId(),
                    now
            );

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("_export_meta", meta);

            // Meta line is excluded from digest/signature (rawOut only)
            digestingExportService.writeMetaJsonl(envelope, ctx);
            ctx.flush();

            if (afterSuccess != null) {
                try {
                    afterSuccess.run();
                } catch (Exception ignored) {
                    // strict: never break snapshot integrity due to post-hook
                }
            }

            emitExportLog(
                    true,
                    correlationId,
                    correlationSource,
                    snapshot.getId(),
                    stream,
                    tenantId,
                    exported,
                    payloadDigestHex,
                    sig.keyId(),
                    sig.algorithm()
            );

            metrics.incrementSuccess(STREAM, EXEC_CTX);

        } catch (RuntimeException e) {

            emitExportLog(
                    false,
                    correlationId,
                    correlationSource,
                    snapshot != null ? snapshot.getId() : null,
                    stream,
                    tenantId,
                    exported,
                    snapshot != null ? snapshot.getSha256DigestHex() : null,
                    snapshot != null ? snapshot.getKeyId() : null,
                    snapshot != null ? snapshot.getSignatureAlg() : null,
                    kv("exception.class", e.getClass().getSimpleName())
            );

            metrics.incrementFailure(STREAM, EXEC_CTX, e);
            throw e;

        } catch (Exception e) {

            emitExportLog(
                    false,
                    correlationId,
                    correlationSource,
                    snapshot != null ? snapshot.getId() : null,
                    stream,
                    tenantId,
                    exported,
                    snapshot != null ? snapshot.getSha256DigestHex() : null,
                    snapshot != null ? snapshot.getKeyId() : null,
                    snapshot != null ? snapshot.getSignatureAlg() : null,
                    kv("exception.class", e.getClass().getSimpleName())
            );

            metrics.incrementFailure(STREAM, EXEC_CTX, e);
            throw new IllegalStateException("Forensic JSONL export failed", e);

        } finally {
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private static String resolveCorrelationId() {
        String corr = MDC.get("correlationId");
        if (corr != null && !corr.isBlank()) return corr;

        // Export should be called in HTTP context where filter sets correlationId;
        // but this is a safety net for strict traceability.
        return "export-" + UUID.randomUUID();
    }

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(MDC.get(RequestCorrelationIdFilter.MDC_SOURCE_KEY))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private static void emitExportLog(
            boolean ok,
            String correlationId,
            CorrelationSource correlationSource,
            UUID snapshotId,
            String exportStream,
            UUID tenantId,
            long rowCount,
            String payloadSha256Hex,
            String keyId,
            String signatureAlg,
            StructuredArgument... extra
    ) {

        StructuredArgument[] base = new StructuredArgument[]{
                kv("schema_version", "docflow_siem_v1"),
                kv("event.category", "audit"),
                kv("event.action", ok ? "SEALED_EXPORT_OK" : "SEALED_EXPORT_FAILED"),
                kv("event.outcome", ok ? "success" : "failure"),
                kv("audit.stream", STREAM),
                kv("execution.context", EXEC_CTX),
                kv("correlation.id", correlationId),
                kv("correlation.source", correlationSource.name()),
                kv("snapshot.id", snapshotId),
                kv("snapshot.stream", exportStream),
                kv("tenant.id", tenantId),
                kv("row.count", rowCount),
                kv("payload.sha256", payloadSha256Hex),
                kv("key.id", keyId),
                kv("signature.alg", signatureAlg)
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
}