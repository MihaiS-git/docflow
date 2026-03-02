package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditExportVerificationResultDTO;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKeyResolver;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import net.logstash.logback.argument.StructuredArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AuditExportVerificationService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final HexFormat HEX = HexFormat.of();

    private static final String STREAM = "AUDIT_EXPORT_VERIFICATION";
    private static final String EXEC_CTX = ExecutionContext.HTTP.name();

    // Hard-enforced algorithms (no external input)
    private static final String DIGEST_ALG = "SHA-256";
    private static final String SIGNATURE_ALG = "SHA256withRSA";

    private final AuditExportSnapshotRepository snapshotRepository;
    private final AuditSigningKeyResolver keyResolver;
    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalJsonWriter;
    private final AuditWriteFailureMetrics metrics;
    private final long maxUploadBytes;

    public AuditExportVerificationService(
            AuditExportSnapshotRepository snapshotRepository,
            AuditSigningKeyResolver keyResolver,
            ObjectMapper objectMapper,
            AuditWriteFailureMetrics metrics,
            @Value("${docflow.audit.export.verify.max-bytes:268435456}") long maxUploadBytes
    ) {
        this.snapshotRepository = snapshotRepository;
        this.keyResolver = keyResolver;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.maxUploadBytes = maxUploadBytes;

        // Deterministic meta canonicalization (sorted keys, no pretty print)
        ObjectMapper canonical = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(SerializationFeature.INDENT_OUTPUT)
                .build();

        this.canonicalJsonWriter = canonical.writer();
    }

    @Transactional(readOnly = true)
    public AuditExportVerificationResultDTO verifySnapshotFile(UUID snapshotId, MultipartFile file) {

        final long startNs = System.nanoTime();
        String correlationId = resolveCorrelationId(snapshotId);
        CorrelationSource correlationSource = resolveCorrelationSource();

        AuditExportSnapshot snapshot = null;

        try {

            if (file == null || file.isEmpty()) {
                return fail(snapshotId, null, null,
                        "NO_FILE", "No file provided",
                        correlationId, correlationSource);
            }

            long declaredSize = file.getSize();
            if (declaredSize > 0 && declaredSize > maxUploadBytes) {
                return fail(snapshotId, null, null,
                        "FILE_TOO_LARGE", "File too large",
                        correlationId, correlationSource);
            }

            snapshot = snapshotRepository.findById(snapshotId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Snapshot not found: " + snapshotId));

            // Disallow external signatureAlgorithm input entirely: registry must be our expected algorithm.
            if (snapshot.getSignatureAlg() == null
                    || !SIGNATURE_ALG.equals(snapshot.getSignatureAlg().trim())) {
                return fail(snapshotId, snapshot.getStream(), snapshot.getTenantId(),
                        "UNSUPPORTED_SIGNATURE_ALG",
                        "Unsupported signature algorithm in registry: " + snapshot.getSignatureAlg(),
                        correlationId, correlationSource);
            }

            StreamingVerifyResult streaming = streamVerifyAndExtractMeta(snapshot, file);

            boolean digestMatches =
                    streaming.computedDigestHex.equalsIgnoreCase(snapshot.getSha256DigestHex());

            boolean keyExists = keyResolver.exists(snapshot.getKeyId());

            boolean signatureValid = false;
            if (keyExists) {
                signatureValid = streaming.signatureValid;
            }

            boolean fingerprintMatches = false;
            if (keyExists) {
                AuditSigningKey keyRow =
                        keyResolver.findById(snapshot.getKeyId()).orElse(null);

                if (keyRow != null && streaming.meta.publicKeyFingerprint() != null) {
                    fingerprintMatches =
                            streaming.meta.publicKeyFingerprint()
                                    .equalsIgnoreCase(keyRow.getFingerprintSha256Hex());
                }
            }

            boolean ok =
                    digestMatches &&
                            keyExists &&
                            signatureValid &&
                            fingerprintMatches;

            emitVerificationLog(
                    ok,
                    correlationId,
                    correlationSource,
                    snapshot.getId(),
                    snapshot.getStream(),
                    snapshot.getTenantId(),
                    ok ? "VERIFY_OK" : "VERIFY_FAILED",
                    ok ? "OK" : "FAILED",
                    kv("verify.digest_matches", digestMatches),
                    kv("verify.signature_valid", signatureValid),
                    kv("verify.key_exists", keyExists),
                    kv("verify.fingerprint_matches", fingerprintMatches),
                    kv("verify.payload_length_bytes", streaming.payloadLengthBytes)
            );

            if (ok) {
                metrics.incrementSuccess(STREAM, EXEC_CTX);
            } else {
                metrics.incrementFailure(STREAM, EXEC_CTX,
                        new IllegalStateException("Verification failed"));
            }

            return new AuditExportVerificationResultDTO(
                    ok,
                    snapshot.getId(),
                    snapshot.getStream(),
                    snapshot.getTenantId(),
                    snapshot.getFromTs(),
                    snapshot.getToTs(),
                    snapshot.getRowCount(),
                    snapshot.getSha256DigestHex(),
                    streaming.computedDigestHex,
                    digestMatches,
                    keyExists,
                    signatureValid && fingerprintMatches,
                    snapshot.getSignatureAlg(),
                    snapshot.getKeyId(),
                    true, true, true, true, true, true,
                    true, true, true, true, true, true, true,
                    ok ? "OK" : "FAILED"
            );

        } catch (IllegalArgumentException e) {
            // Deterministic validation failures become a structured failure response (not a 500)
            if (snapshot != null) {
                return fail(snapshotId, snapshot.getStream(), snapshot.getTenantId(),
                        "VERIFY_INVALID", e.getMessage(),
                        correlationId, correlationSource);
            }
            return fail(snapshotId, null, null,
                    "VERIFY_INVALID", e.getMessage(),
                    correlationId, correlationSource);

        } catch (Exception e) {
            metrics.incrementFailure(STREAM, EXEC_CTX, e);
            throw new IllegalStateException("Verification failed", e);
        } finally {
            metrics.recordLatency(STREAM, EXEC_CTX,
                    Duration.ofNanos(System.nanoTime() - startNs));
        }
    }

    /* =====================================================
       STREAM-ONLY VERIFICATION (NO TEMP FILE)
       ===================================================== */

    private StreamingVerifyResult streamVerifyAndExtractMeta(
            AuditExportSnapshot snapshot,
            MultipartFile file
    ) throws Exception {

        if (snapshot.getKeyId() == null || snapshot.getKeyId().isBlank()) {
            throw new IllegalArgumentException("Registry keyId missing");
        }
        if (snapshot.getSignatureB64() == null || snapshot.getSignatureB64().isBlank()) {
            throw new IllegalArgumentException("Registry signatureB64 missing");
        }
        if (snapshot.getSha256DigestHex() == null || snapshot.getSha256DigestHex().isBlank()) {
            throw new IllegalArgumentException("Registry sha256DigestHex missing");
        }

        // Prepare verifier from registry ONLY (no external signatureAlgorithm input).
        PublicKey pk = keyResolver.requirePublicKey(snapshot.getKeyId().trim());
        Signature verifier = Signature.getInstance(SIGNATURE_ALG);
        verifier.initVerify(pk);

        byte[] signatureBytes = Base64.getDecoder().decode(snapshot.getSignatureB64());

        MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);

        // Keep last 2 lines uncommitted so we can tolerate a trailing blank newline line.
        List<byte[]> tailLines = new ArrayList<>(2);

        long totalRead = 0L;
        long payloadLengthBytes = 0L;

        // Current line buffer (bytes since last '\n')
        ByteArrayOutputStreamEx currentLine = new ByteArrayOutputStreamEx(16 * 1024);

        try (InputStream in = new BufferedInputStream(file.getInputStream())) {

            byte[] buf = new byte[64 * 1024];
            int r;

            while ((r = in.read(buf)) != -1) {

                totalRead += r;
                if (totalRead > maxUploadBytes) {
                    throw new IllegalArgumentException("Upload exceeds limit");
                }

                for (int i = 0; i < r; i++) {
                    byte b = buf[i];
                    currentLine.writeByte(b);

                    if (b == (byte) '\n') {
                        byte[] completedLine = currentLine.toByteArrayAndReset();

                        tailLines.add(completedLine);
                        if (tailLines.size() > 2) {
                            byte[] commit = tailLines.removeFirst();
                            payloadLengthBytes += commit.length;
                            md.update(commit);
                            verifier.update(commit);
                        }
                    }
                }
            }
        }

        // If file doesn't end with '\n', treat remaining as a final line (no newline).
        if (currentLine.size() > 0) {
            byte[] finalLineNoNl = currentLine.toByteArrayAndReset();
            tailLines.add(finalLineNoNl);
            if (tailLines.size() > 2) {
                byte[] commit = tailLines.removeFirst();
                payloadLengthBytes += commit.length;
                md.update(commit);
                verifier.update(commit);
            }
        }

        // Choose meta line: last non-empty line among tailLines (ignoring pure newline / CRLF).
        int metaIndex = -1;
        String metaLineUtf8 = null;

        for (int i = tailLines.size() - 1; i >= 0; i--) {
            String candidate = trimLineEndingsUtf8(tailLines.get(i));
            if (!candidate.isBlank()) {
                metaIndex = i;
                metaLineUtf8 = candidate;
                break;
            }
        }

        if (metaIndex == -1) {
            throw new IllegalArgumentException("Missing _export_meta line");
        }

        // Anything after meta must be blank line(s) only (otherwise trailing data after meta)
        for (int i = metaIndex + 1; i < tailLines.size(); i++) {
            String after = trimLineEndingsUtf8(tailLines.get(i));
            if (!after.isBlank()) {
                throw new IllegalArgumentException("Trailing data after meta line");
            }
        }

        // Commit any non-meta lines that were kept in tailLines before metaIndex.
        for (int i = 0; i < metaIndex; i++) {
            byte[] commit = tailLines.get(i);
            payloadLengthBytes += commit.length;
            md.update(commit);
            verifier.update(commit);
        }

        // Strict structural detection + deterministic canonicalization + full alignment checks.
        AuditExportMetadataDTO meta = parseAndValidateMetaStrict(metaLineUtf8, snapshot);

        byte[] computedDigestBytes = md.digest();
        String computedDigestHex = HEX.formatHex(computedDigestBytes);

        boolean signatureValid = verifier.verify(signatureBytes);

        return new StreamingVerifyResult(meta, computedDigestHex, signatureValid, payloadLengthBytes);
    }

    private AuditExportMetadataDTO parseAndValidateMetaStrict(
            String metaLineUtf8,
            AuditExportSnapshot snapshot
    ) {
        try {
            // Structural meta detection (no substring checks)
            JsonNode root = objectMapper.readTree(metaLineUtf8);
            JsonNode metaNode = root.get("_export_meta");
            if (metaNode == null || !metaNode.isObject()) {
                throw new IllegalArgumentException("Invalid meta line: missing _export_meta object");
            }

            // Deterministic canonicalization enforcement:
            // metaLine must be EXACT canonical serialization of the parsed root.
            String canonical = canonicalJsonWriter.writeValueAsString(root);
            if (!canonical.equals(metaLineUtf8)) {
                throw new IllegalArgumentException("Meta line is not canonical JSON");
            }

            AuditExportMetadataDTO meta =
                    objectMapper.treeToValue(metaNode, AuditExportMetadataDTO.class);

            // Explicit digest algorithm enforcement (SHA-256 only)
            if (meta.digestAlgorithm() == null || meta.digestAlgorithm().isBlank()) {
                throw new IllegalArgumentException("Invalid meta: digestAlgorithm missing");
            }
            if (!DIGEST_ALG.equals(meta.digestAlgorithm().trim())) {
                throw new IllegalArgumentException("Unsupported digestAlgorithm: " + meta.digestAlgorithm());
            }

            // Disallow external signatureAlgorithm input entirely:
            // meta must match registry and registry must match our fixed algorithm.
            if (meta.signatureAlgorithm() == null || meta.signatureAlgorithm().isBlank()) {
                throw new IllegalArgumentException("Invalid meta: signatureAlgorithm missing");
            }
            if (!SIGNATURE_ALG.equals(meta.signatureAlgorithm().trim())) {
                throw new IllegalArgumentException("Unsupported signatureAlgorithm: " + meta.signatureAlgorithm());
            }
            if (!SIGNATURE_ALG.equals(snapshot.getSignatureAlg().trim())) {
                throw new IllegalArgumentException("Registry signatureAlgorithm unsupported: " + snapshot.getSignatureAlg());
            }

            // Required fields
            if (meta.snapshotId() == null) throw new IllegalArgumentException("Invalid meta: snapshotId missing");
            if (meta.payloadSha256Hex() == null || meta.payloadSha256Hex().isBlank())
                throw new IllegalArgumentException("Invalid meta: payloadSha256Hex missing");
            if (meta.signatureB64() == null || meta.signatureB64().isBlank())
                throw new IllegalArgumentException("Invalid meta: signatureB64 missing");
            if (meta.keyId() == null || meta.keyId().isBlank())
                throw new IllegalArgumentException("Invalid meta: keyId missing");
            if (meta.publicKeyFingerprint() == null || meta.publicKeyFingerprint().isBlank())
                throw new IllegalArgumentException("Invalid meta: publicKeyFingerprint missing");
            if (meta.signatureInput() == null || meta.signatureInput().isBlank())
                throw new IllegalArgumentException("Invalid meta: signatureInput missing");

            // Registry alignment checks (full)
            if (!meta.snapshotId().equals(snapshot.getId())) {
                throw new IllegalArgumentException("Meta snapshotId mismatch");
            }
            if (!safeEq(meta.stream(), snapshot.getStream())) {
                throw new IllegalArgumentException("Meta stream mismatch");
            }
            if (!meta.keyId().trim().equals(snapshot.getKeyId().trim())) {
                throw new IllegalArgumentException("Meta keyId mismatch");
            }
            if (!meta.signatureAlgorithm().trim().equals(snapshot.getSignatureAlg().trim())) {
                throw new IllegalArgumentException("Meta signatureAlgorithm mismatch");
            }
            if (!meta.signatureB64().trim().equals(snapshot.getSignatureB64().trim())) {
                throw new IllegalArgumentException("Meta signatureB64 mismatch");
            }
            if (!meta.payloadSha256Hex().trim().equalsIgnoreCase(snapshot.getSha256DigestHex().trim())) {
                throw new IllegalArgumentException("Meta payloadSha256Hex mismatch");
            }
            if (!AuditExportMetadataDTO.SIGNATURE_INPUT_PAYLOAD_JSONL_BYTES.equals(meta.signatureInput())) {
                throw new IllegalArgumentException("Unsupported signatureInput: " + meta.signatureInput());
            }

            return meta;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse meta line: " + e.getMessage(), e);
        }
    }

    private static String trimLineEndingsUtf8(byte[] lineBytes) {
        if (lineBytes == null || lineBytes.length == 0) return "";
        int end = lineBytes.length;
        while (end > 0) {
            byte b = lineBytes[end - 1];
            if (b == '\n' || b == '\r') end--;
            else break;
        }
        if (end <= 0) return "";
        return new String(lineBytes, 0, end, StandardCharsets.UTF_8);
    }

    private record StreamingVerifyResult(
            AuditExportMetadataDTO meta,
            String computedDigestHex,
            boolean signatureValid,
            long payloadLengthBytes
    ) {}

    private static final class ByteArrayOutputStreamEx {
        private byte[] buf;
        private int count;

        private ByteArrayOutputStreamEx(int initialCapacity) {
            this.buf = new byte[Math.max(64, initialCapacity)];
            this.count = 0;
        }

        int size() {
            return count;
        }

        void writeByte(byte b) {
            if (count == buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            buf[count++] = b;
        }

        byte[] toByteArrayAndReset() {
            byte[] out = Arrays.copyOf(buf, count);
            count = 0;
            return out;
        }
    }

    /* =====================================================
       LOGGING + METRICS HELPERS
       ===================================================== */

    private static String resolveCorrelationId(UUID snapshotId) {
        String corr = MDC.get("correlationId");
        if (corr != null && !corr.isBlank()) return corr;
        return "verify-" + snapshotId;
    }

    private static CorrelationSource resolveCorrelationSource() {
        return "GENERATED".equalsIgnoreCase(
                MDC.get(RequestCorrelationIdFilter.MDC_SOURCE_KEY))
                ? CorrelationSource.GENERATED
                : CorrelationSource.REQUEST_ID;
    }

    private AuditExportVerificationResultDTO fail(
            UUID snapshotId,
            String stream,
            UUID tenantId,
            String action,
            String message,
            String correlationId,
            CorrelationSource source
    ) {

        emitVerificationLog(false, correlationId, source,
                snapshotId, stream, tenantId,
                action, message);

        metrics.incrementFailure(STREAM, EXEC_CTX,
                new IllegalStateException(message));

        return new AuditExportVerificationResultDTO(
                false,
                snapshotId,
                stream,
                tenantId,
                null,
                null,
                0L,
                null,
                null,
                false,
                false,
                false,
                null,
                null,
                false, false, false, false, false, false,
                false, false, false, false, false, false, false,
                message
        );
    }

    private static void emitVerificationLog(
            boolean ok,
            String correlationId,
            CorrelationSource correlationSource,
            UUID snapshotId,
            String snapshotStream,
            UUID tenantId,
            String action,
            String message,
            StructuredArgument... extra
    ) {

        StructuredArgument[] base = new StructuredArgument[]{
                kv("schema_version", "docflow_siem_v1"),
                kv("event.category", "audit"),
                kv("event.action", action),
                kv("event.outcome", ok ? "success" : "failure"),
                kv("audit.stream", STREAM),
                kv("execution.context", EXEC_CTX),
                kv("correlation.id", correlationId),
                kv("correlation.source", correlationSource.name()),
                kv("snapshot.id", snapshotId),
                kv("snapshot.stream", snapshotStream),
                kv("tenant.id", tenantId),
                kv("message", message)
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

    private static boolean safeEq(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}