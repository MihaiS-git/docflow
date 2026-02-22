package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.brutecx.docflow_backend.api.dto.audit.BaseAuditForensicExportDTO;
import com.brutecx.docflow_backend.domain.audit.forensic.DigestingForensicExportService;
import com.brutecx.docflow_backend.domain.security.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.AuditSigningKeyRotationService;
import com.brutecx.docflow_backend.domain.security.ExportSigningService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class SealedJsonlAuditExportService {

    public static final int DEFAULT_META_VERSION = 1;

    private static final DateTimeFormatter FILENAME_TS_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC);

    @FunctionalInterface
    public interface CursorBatchFetcher<E> {
        Page<E> fetch(Instant cursorTimestamp, UUID cursorId);
    }

    private final DigestingForensicExportService digestingExportService;
    private final ExportSigningService exportSigningService;
    private final AuditSigningKeyRotationService rotationService;
    private final AuditExportSnapshotRepository auditExportSnapshotRepository;
    private final UserService userService;

    @Transactional
    public <E, T extends BaseAuditForensicExportDTO> void exportSealedJsonl(
            HttpServletResponse response,
            String stream,
            Instant from,
            Instant to,
            UUID tenantId,
            int exportMaxRows,
            CursorBatchFetcher<E> fetcher,
            Function<E, T> mapper,
            Runnable afterSuccess
    ) {

        if (stream == null || stream.isBlank())
            throw new IllegalArgumentException("stream required");

        if (from == null || to == null)
            throw new IllegalArgumentException("from/to required");

        if (from.isAfter(to))
            throw new IllegalArgumentException("Invalid range: from > to");

        if (exportMaxRows <= 0)
            throw new IllegalArgumentException("exportMaxRows must be > 0");

        // Ensure active key exists before writing response
        AuditSigningKey activeKey = rotationService.requireActiveForExport();

        response.setContentType("application/x-ndjson");
        response.setCharacterEncoding("UTF-8");

        String slug = toSlug(stream);
        String fromToken = formatInstantForFilename(from);
        String toToken = formatInstantForFilename(to);

        String filename =
                slug +
                        "_from_" + fromToken +
                        "_to_" + toToken +
                        ".jsonl";

        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\""
        );

        User actor = userService.getRequiredCurrentUser();

        DigestingForensicExportService.ExportDigestContext ctx;
        ExportSigningService.PayloadSigner signer;

        try {
            OutputStream out = response.getOutputStream();

            // Start signer first, then wire it into the digest stream so BOTH digest + signature
            // are updated from the exact same payload bytes.
            signer = exportSigningService.beginPayloadSigner();
            ctx = digestingExportService.beginDigestStream(out, signer.signature());

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize export stream", e);
        }

        long exported = 0L;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        try {
            while (true) {

                Page<E> batch = fetcher.fetch(cursorTimestamp, cursorId);
                if (batch == null || batch.isEmpty())
                    break;

                for (E entity : batch.getContent()) {

                    if (exported >= exportMaxRows)
                        throw new IllegalStateException("Export row limit exceeded: " + exportMaxRows);

                    T dto = mapper.apply(entity);

                    if (dto == null)
                        throw new IllegalStateException("Mapper returned null DTO");

                    if (dto.timestamp() == null || dto.id() == null)
                        throw new IllegalStateException("DTO must provide timestamp and id");

                    // This writes the payload JSONL line and updates BOTH:
                    // - SHA-256(payload bytes)
                    // - RSA signature over payload bytes
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

            AuditExportSnapshot snapshot = new AuditExportSnapshot(
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
                    // do not break snapshot integrity
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Forensic JSONL export failed", e);
        }
    }

    private static String formatInstantForFilename(Instant ts) {
        return FILENAME_TS_UTC.format(ts);
    }

    private static String toSlug(String stream) {
        String lower = stream.toLowerCase(Locale.ROOT);
        return lower.replace('_', '-');
    }
}