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
import java.util.*;
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
        final String correlationId = resolveCorrelationId();
        final CorrelationSource correlationSource = resolveCorrelationSource();

        AuditExportSnapshot snapshot = null;
        long exported = 0L;

        try {
            Objects.requireNonNull(out, "out required");
            Objects.requireNonNull(stream, "stream required");
            Objects.requireNonNull(from, "from required");
            Objects.requireNonNull(to, "to required");

            if (from.isAfter(to)) {
                throw new IllegalArgumentException("Invalid range: from > to");
            }

            AuditSigningKey activeKey = rotationService.requireActiveForExport();
            User actor = userService.getRequiredCurrentUser();

            ExportSigningService.PayloadSigner signer =
                    exportSigningService.beginPayloadSigner(activeKey);

            DigestingForensicExportService.ExportDigestContext ctx =
                    digestingExportService.beginDigestStream(out, signer.signature());

            Instant cursorTimestamp = null;
            UUID cursorId = null;

            while (true) {
                Page<E> batch = fetcher.fetch(cursorTimestamp, cursorId);
                if (batch == null || batch.isEmpty()) break;

                for (E entity : batch.getContent()) {

                    if (exported >= exportMaxRows) {
                        throw new IllegalStateException("Export row limit exceeded");
                    }

                    T dto = mapper.apply(entity);

                    if (dto == null || dto.timestamp() == null || dto.id() == null) {
                        throw new IllegalStateException("Invalid DTO for export");
                    }

                    digestingExportService.writePayloadJsonl(dto, ctx);

                    exported++;
                    cursorTimestamp = dto.timestamp();
                    cursorId = dto.id();
                }
            }

            byte[] digestBytes = ctx.finalizePayloadDigest();
            String digestHex = DigestingForensicExportService.hexSha256(digestBytes);

            if (digestHex == null || digestHex.isBlank()) {
                throw new IllegalStateException("Computed digest is invalid");
            }

            ExportSigningService.SignatureResult sig = signer.finish();

            Instant now = Instant.now();

            snapshot = new AuditExportSnapshot(
                    stream,
                    from,
                    to,
                    tenantId,
                    digestHex,
                    exported,
                    now,
                    actor.getId(),
                    sig.signatureB64(),
                    sig.algorithm(),
                    sig.keyId()
            );

            auditExportSnapshotRepository.saveAndFlush(snapshot);

            AuditExportMetadataDTO meta = new AuditExportMetadataDTO(
                    DEFAULT_META_VERSION,
                    stream,
                    from,
                    to,
                    tenantId,
                    exported,
                    digestHex,
                    sig.signatureB64(),
                    sig.algorithm(),
                    sig.keyId(),
                    activeKey.getFingerprintSha256Hex(),
                    activeKey.getPublicKeyPem(),
                    AuditExportMetadataDTO.DIGEST_ALG_SHA256,
                    AuditExportMetadataDTO.SIGNATURE_INPUT_PAYLOAD_JSONL_BYTES,
                    snapshot.getId(),
                    now
            );

            digestingExportService.writeMetaJsonl(
                    Map.of("_export_meta", meta),
                    ctx
            );

            ctx.flush();
            // Prevents afterSuccess.run() if the user disconnected and the export is never delivered
            out.flush(); // ensure servlet container pushes bytes

            emitExportLog(
                    true,
                    correlationId,
                    correlationSource,
                    snapshot.getId(),
                    stream,
                    tenantId,
                    exported,
                    digestHex,
                    sig.keyId(),
                    sig.algorithm()
            );

            metrics.incrementSuccess(STREAM, EXEC_CTX);

            if (afterSuccess != null) {
                afterSuccess.run();
            }
        } catch (Exception e) {
            emitExportLog(
                    false,
                    correlationId,
                    correlationSource,
                    snapshot != null ? snapshot.getId() : null,
                    stream,
                    tenantId,
                    exported,
                    null,
                    null,
                    null,
                    kv("exception.class", e.getClass().getSimpleName())
            );

            metrics.incrementFailure(STREAM, EXEC_CTX, e);
            throw new IllegalStateException("Sealed export failed", e);
        } finally {
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    private static String resolveCorrelationId() {
        String corr = MDC.get("correlationId");
        return (corr != null && !corr.isBlank())
                ? corr
                : "export-" + UUID.randomUUID();
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
        List<StructuredArgument> args = new ArrayList<>();

        args.add(kv("schema_version", "docflow_siem_v1"));
        args.add(kv("event.category", "audit"));
        args.add(kv("event.action", ok ? "SEALED_EXPORT_OK" : "SEALED_EXPORT_FAILED"));
        args.add(kv("event.outcome", ok ? "success" : "failure"));
        args.add(kv("audit.stream", STREAM));
        args.add(kv("execution.context", EXEC_CTX));
        args.add(kv("correlation.id", correlationId));
        args.add(kv("correlation.source", correlationSource.name()));
        args.add(kv("snapshot.id", snapshotId));
        args.add(kv("snapshot.stream", exportStream));
        args.add(kv("tenant.id", tenantId));
        args.add(kv("row.count", rowCount));
        args.add(kv("payload.sha256", payloadSha256Hex));
        args.add(kv("key.id", keyId));
        args.add(kv("signature.alg", signatureAlg));

        if (extra != null) {
            args.addAll(Arrays.asList(extra));
        }

        if (ok) {
            log.info("security_event {}", args.toArray());
        } else {
            log.error("security_event {}", args.toArray());
        }
    }
}