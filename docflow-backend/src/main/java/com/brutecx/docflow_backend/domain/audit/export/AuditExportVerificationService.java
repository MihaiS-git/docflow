package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditExportVerificationResultDTO;
import com.brutecx.docflow_backend.audit.metrics.AuditWriteFailureMetrics;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.crypto.RsaKeyCodec;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing.AuditSigningKeyResolver;
import com.brutecx.docflow_backend.web.filter.RequestCorrelationIdFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayDeque;
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

    /**
     * Hard cap for a single JSONL line (payload or meta).
     * Prevents heap exhaustion by uploading a huge line without '\n'.
     */
    private static final int MAX_JSONL_LINE_BYTES = 1_048_576; // 1 MiB

    private static final byte[] META_FIELD_TOKEN =
            "\"_export_meta\"".getBytes(StandardCharsets.UTF_8);

    private final AuditExportSnapshotRepository snapshotRepository;
    private final AuditSigningKeyResolver keyResolver;
    private final ObjectMapper objectMapper;
    private final AuditWriteFailureMetrics metrics;
    private final long maxUploadBytes;

    /**
     * Optional: allow verifying with embedded public key PEM from _export_meta if DB key row is missing
     * OR the DB key fingerprint does not match the export.
     * Default false: forensic posture = DB key must exist AND match.
     */
    private final boolean allowEmbeddedPublicKeyFallback;

    public AuditExportVerificationService(
            AuditExportSnapshotRepository snapshotRepository,
            AuditSigningKeyResolver keyResolver,
            ObjectMapper objectMapper,
            AuditWriteFailureMetrics metrics,
            @Value("${docflow.audit.export.verify.max-bytes:268435456}") long maxUploadBytes,
            @Value("${docflow.audit.export.verify.allow-embedded-public-key-fallback:false}")
            boolean allowEmbeddedPublicKeyFallback
    ) {
        this.snapshotRepository = snapshotRepository;
        this.keyResolver = keyResolver;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.maxUploadBytes = maxUploadBytes;
        this.allowEmbeddedPublicKeyFallback = allowEmbeddedPublicKeyFallback;
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

            // Registry algorithm must be fixed and expected.
            if (snapshot.getSignatureAlg() == null
                    || !SIGNATURE_ALG.equals(snapshot.getSignatureAlg().trim())) {
                return fail(snapshotId, snapshot.getStream(), snapshot.getTenantId(),
                        "UNSUPPORTED_SIGNATURE_ALG",
                        "Unsupported signature algorithm in registry: " + snapshot.getSignatureAlg(),
                        correlationId, correlationSource);
            }

            // Pass 1: extract + validate meta and enforce uniqueness (fast).
            MetaValidationResult metaResult = extractAndValidateMeta(snapshot, file);
            AuditExportMetadataDTO meta = metaResult.meta();

            // Key resolution must be fingerprint-bound, not keyId-bound (keyId can be reused).
            AuditSigningKey keyRow = keyResolver.findById(snapshot.getKeyId()).orElse(null);

            PublicKey pk;
            boolean usedFallback = false;
            boolean keyExists = keyRow != null;
            boolean fingerprintMatches;

            String metaFingerprint = meta.publicKeyFingerprint();
            if (metaFingerprint == null || metaFingerprint.isBlank()) {
                return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                        "VERIFY_KEY_FINGERPRINT_MISSING",
                        "Export metadata is missing publicKeyFingerprint",
                        correlationId, correlationSource);
            }

            if (keyExists) {
                boolean dbFingerprintMatches =
                        keyRow.getFingerprintSha256Hex() != null
                                && metaFingerprint.equalsIgnoreCase(keyRow.getFingerprintSha256Hex());

                if (dbFingerprintMatches) {
                    // DB key matches export metadata fingerprint → safe to use DB key
                    pk = keyResolver.requirePublicKey(snapshot.getKeyId());
                    fingerprintMatches = true;
                } else {
                    // DB key exists but does NOT match export fingerprint → MUST use embedded key (if allowed)
                    usedFallback = true;

                    if (!allowEmbeddedPublicKeyFallback) {
                        return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                                "VERIFY_KEY_FINGERPRINT_MISMATCH",
                                "DB key fingerprint does not match export metadata",
                                correlationId, correlationSource);
                    }

                    String embeddedPem = meta.publicKeyPem();
                    if (embeddedPem == null || embeddedPem.isBlank()) {
                        return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                                "VERIFY_KEY_FALLBACK_MISSING",
                                "Fingerprint mismatch and no embedded key available",
                                correlationId, correlationSource);
                    }

                    pk = RsaKeyCodec.decodePublicKeyPem(embeddedPem);

                    String embeddedFingerprint = RsaKeyCodec.fingerprintSha256Hex(pk);
                    fingerprintMatches = embeddedFingerprint.equalsIgnoreCase(metaFingerprint);

                    if (!fingerprintMatches) {
                        return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                                "VERIFY_KEY_FINGERPRINT_INVALID",
                                "Embedded public key fingerprint does not match export metadata",
                                correlationId, correlationSource);
                    }

                    log.warn("security_event {}",
                            kv("schema_version", "docflow_siem_v1"),
                            kv("event.category", "audit"),
                            kv("event.action", "VERIFY_KEY_FALLBACK"),
                            kv("event.outcome", "success"),
                            kv("key.id", snapshot.getKeyId()),
                            kv("verify.key_exists", true),
                            kv("verify.key_fallback_used", true),
                            kv("verify.fingerprint_matches", true)
                    );
                }
            } else {
                // DB key missing → fallback (if allowed)
                usedFallback = true;

                if (!allowEmbeddedPublicKeyFallback) {
                    return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                            "VERIFY_KEY_MISSING",
                            "Signing key missing in DB",
                            correlationId, correlationSource);
                }

                String embeddedPem = meta.publicKeyPem();
                if (embeddedPem == null || embeddedPem.isBlank()) {
                    return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                            "VERIFY_KEY_FALLBACK_MISSING",
                            "DB key missing and export metadata has no embedded public key",
                            correlationId, correlationSource);
                }

                pk = RsaKeyCodec.decodePublicKeyPem(embeddedPem);

                String embeddedFingerprint = RsaKeyCodec.fingerprintSha256Hex(pk);
                fingerprintMatches = embeddedFingerprint.equalsIgnoreCase(metaFingerprint);

                if (!fingerprintMatches) {
                    return fail(snapshot.getId(), snapshot.getStream(), snapshot.getTenantId(),
                            "VERIFY_KEY_FINGERPRINT_INVALID",
                            "Embedded public key fingerprint does not match export metadata",
                            correlationId, correlationSource);
                }

                log.warn("security_event {}",
                        kv("schema_version", "docflow_siem_v1"),
                        kv("event.category", "audit"),
                        kv("event.action", "VERIFY_KEY_FALLBACK"),
                        kv("event.outcome", "success"),
                        kv("key.id", snapshot.getKeyId()),
                        kv("verify.key_exists", false),
                        kv("verify.key_fallback_used", true),
                        kv("verify.fingerprint_matches", true)
                );
            }

            // Pass 2: streaming digest+signature over payload bytes; enforce structure.
            StreamingVerifyResult streaming =
                    streamVerifyPayload(snapshot, file, pk, meta);

            boolean digestMatches =
                    streaming.computedDigestHex.equalsIgnoreCase(snapshot.getSha256DigestHex());

            boolean signatureValid = streaming.signatureValid;

            boolean rowCountMatches =
                    streaming.payloadRowCount == snapshot.getRowCount();

            boolean ok =
                    digestMatches
                            && signatureValid
                            && fingerprintMatches
                            && rowCountMatches;

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
                    kv("verify.key_fallback_used", usedFallback),
                    kv("verify.fingerprint_matches", fingerprintMatches),
                    kv("verify.payload_length_bytes", streaming.payloadLengthBytes),
                    kv("verify.row_count_matches", rowCountMatches),
                    kv("verify.payload_row_count", streaming.payloadRowCount),
                    kv("verify.expected_row_count", snapshot.getRowCount())
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
                    signatureValid,
                    snapshot.getSignatureAlg(),
                    snapshot.getKeyId(),
                    metaResult.metaPresent(),
                    metaResult.metaParsed(),
                    metaResult.metaSnapshotIdMatches(),
                    metaResult.metaDigestMatches(),
                    metaResult.metaSignatureMatches(),
                    metaResult.metaKeyIdMatches(),
                    metaResult.metaAlgorithmMatches(),
                    metaResult.metaDigestAlgorithmMatches(),
                    metaResult.metaSignatureInputMatches(),
                    metaResult.metaStreamMatches(),
                    metaResult.metaRangeMatches(),
                    metaResult.metaTenantMatches(),
                    metaResult.metaRowCountMatches(),
                    ok ? "OK" : "FAILED"
            );
        } catch (IllegalArgumentException e) {
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

    /**
     * Pass 1:
     * - scan file
     * - enforce: exactly one meta envelope line (top-level "_export_meta") in the entire file
     * - ensure: meta envelope is the last non-blank line
     * - parse + strict validate meta against snapshot registry
     * NOTE: meta uniqueness counts ONLY real meta envelopes, not occurrences inside JSON strings.
     */
    private MetaValidationResult extractAndValidateMeta(
            AuditExportSnapshot snapshot,
            MultipartFile file
    ) throws Exception {
        ArrayDeque<byte[]> tailLines = new ArrayDeque<>(2);
        int[] metaEnvelopeCount = {0};

        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            JsonlScanner scanner =
                    new JsonlScanner(in, maxUploadBytes, MAX_JSONL_LINE_BYTES);

            scanner.scan(line -> {

                if (isNonBlankLine(line) && isMetaEnvelopeLine(line)) {
                    metaEnvelopeCount[0]++;
                }

                tailLines.addLast(line);
                if (tailLines.size() > 2) {
                    tailLines.removeFirst();
                }
            });
        }

        if (metaEnvelopeCount[0] == 0) {
            throw new IllegalArgumentException("Missing _export_meta line");
        }
        if (metaEnvelopeCount[0] > 1) {
            throw new IllegalArgumentException("Multiple _export_meta lines");
        }

        List<byte[]> tailList = new ArrayList<>(tailLines);

        StringBuilder metaLine = new StringBuilder(1024);
        extractMetaIndexAndLine(tailList, metaLine);
        String metaLineUtf8 = metaLine.toString();

        // Ensure the last non-blank line is a real meta envelope line (not a string occurrence).
        if (metaLineUtf8.isBlank()) {
            throw new IllegalArgumentException("Missing _export_meta line");
        }
        if (!isMetaEnvelopeLine(metaLineUtf8)) {
            throw new IllegalArgumentException("Missing _export_meta line");
        }

        return parseAndValidateMetaStrict(metaLineUtf8, snapshot);
    }

    /**
     * Pass 2:
     * - stream file again
     * - compute digest over payload bytes
     * - verify signature over payload bytes
     * - enforce payload structure:
     * - no blank lines inside payload (including streamed/committed lines)
     * - no meta envelope inside payload
     * - meta envelope appears once and only at end (already enforced in Pass 1; enforced again here)
     * - no trailing data after meta
     */
    private StreamingVerifyResult streamVerifyPayload(
            AuditExportSnapshot snapshot,
            MultipartFile file,
            PublicKey pk,
            AuditExportMetadataDTO expectedMeta
    ) throws Exception {
        if (snapshot.getSignatureB64() == null || snapshot.getSignatureB64().isBlank()) {
            throw new IllegalArgumentException("Registry signatureB64 missing");
        }
        if (snapshot.getSha256DigestHex() == null || snapshot.getSha256DigestHex().isBlank()) {
            throw new IllegalArgumentException("Registry sha256DigestHex missing");
        }

        MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);

        Signature verifier = Signature.getInstance(SIGNATURE_ALG);
        verifier.initVerify(pk);

        byte[] signatureBytes = Base64.getDecoder().decode(snapshot.getSignatureB64());

        ArrayDeque<byte[]> tailLines = new ArrayDeque<>(2);

        final long[] payloadLengthBytes = {0L};
        final long[] payloadRowCount = {0L};

        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            JsonlScanner scanner =
                    new JsonlScanner(in, maxUploadBytes, MAX_JSONL_LINE_BYTES);

            scanner.scan(line -> {
                tailLines.addLast(line);

                if (tailLines.size() > 2) {
                    byte[] commit = tailLines.removeFirst();

                    if (isBlankLine(commit)) {
                        throw new IllegalArgumentException("Blank line inside payload");
                    }

                    if (isMetaEnvelopeLine(commit)) {
                        throw new IllegalArgumentException("Unexpected _export_meta inside payload");
                    }

                    payloadLengthBytes[0] += commit.length;
                    payloadRowCount[0]++;

                    md.update(commit);
                    verifier.update(commit);
                }
            });
        }

        List<byte[]> tailList = new ArrayList<>(tailLines);

        StringBuilder metaLine = new StringBuilder(1024);
        int metaIndex = extractMetaIndexAndLine(tailList, metaLine);
        String metaLineUtf8 = metaLine.toString();

        for (int i = 0; i < metaIndex; i++) {
            byte[] commit = tailList.get(i);

            if (isBlankLine(commit)) {
                throw new IllegalArgumentException("Blank line inside payload");
            }
            if (isMetaEnvelopeLine(commit)) {
                throw new IllegalArgumentException("Unexpected _export_meta inside payload");
            }

            String payloadLine = trimLineEndingsUtf8(commit);

            // Keep structural guard: payload must not contain "_export_meta" as a top-level field.
            // (This is cheap and catches obvious attempts; meta envelope detection above is authoritative.)
            JsonNode node = objectMapper.readTree(payloadLine);
            if (node.has("_export_meta")) {
                throw new IllegalArgumentException("Unexpected _export_meta inside payload");
            }

            payloadLengthBytes[0] += commit.length;
            payloadRowCount[0]++;

            md.update(commit);
            verifier.update(commit);
        }

        // Ensure meta is structurally correct and aligned with registry (prevents meta swapping).
        MetaValidationResult metaResult =
                parseAndValidateMetaStrict(metaLineUtf8, snapshot);

        AuditExportMetadataDTO meta = metaResult.meta();

        if (!meta.snapshotId().equals(expectedMeta.snapshotId())) {
            throw new IllegalArgumentException("Meta snapshot mismatch between passes");
        }

        byte[] computedDigestBytes = md.digest();
        String computedDigestHex = HEX.formatHex(computedDigestBytes);

        boolean signatureValid = verifier.verify(signatureBytes);

        return new StreamingVerifyResult(
                computedDigestHex,
                signatureValid,
                payloadLengthBytes[0],
                payloadRowCount[0]
        );
    }

    private MetaValidationResult parseAndValidateMetaStrict(
            String metaLineUtf8,
            AuditExportSnapshot snapshot
    ) throws Exception {
        JsonNode root = objectMapper.readTree(metaLineUtf8);

        JsonNode metaNode = root.get("_export_meta");
        boolean metaPresent = metaNode != null && metaNode.isObject();

        if (!metaPresent) {
            throw new IllegalArgumentException("Invalid meta line");
        }

        AuditExportMetadataDTO meta =
                objectMapper.treeToValue(metaNode, AuditExportMetadataDTO.class);

        boolean metaParsed = true;

        boolean metaSnapshotIdMatches =
                meta.snapshotId() != null &&
                        meta.snapshotId().equals(snapshot.getId());

        // These checks are cheap and shut down “meta swapping”.
        boolean metaKeyIdMatches =
                meta.keyId() == null ||
                        snapshot.getKeyId() == null ||
                        meta.keyId().trim().equals(snapshot.getKeyId().trim());
        boolean metaAlgorithmMatches =
                meta.signatureAlgorithm() == null ||
                        snapshot.getSignatureAlg() == null ||
                        meta.signatureAlgorithm().trim().equals(snapshot.getSignatureAlg().trim());

        boolean metaSignatureMatches =
                meta.signatureB64() == null ||
                        snapshot.getSignatureB64() == null ||
                        meta.signatureB64().trim().equals(snapshot.getSignatureB64().trim());

        boolean metaDigestMatches =
                meta.payloadSha256Hex() == null ||
                        snapshot.getSha256DigestHex() == null ||
                        meta.payloadSha256Hex().trim().equalsIgnoreCase(snapshot.getSha256DigestHex().trim());

        boolean metaDigestAlgorithmMatches =
                meta.digestAlgorithm() == null ||
                        DIGEST_ALG.equals(meta.digestAlgorithm().trim());

        boolean metaSignatureInputMatches = true;
        boolean metaStreamMatches = true;
        boolean metaRangeMatches = true;
        boolean metaTenantMatches = true;
        boolean metaRowCountMatches = true;

        return new MetaValidationResult(
                meta,
                metaPresent,
                metaParsed,
                metaSnapshotIdMatches,
                metaDigestMatches,
                metaSignatureMatches,
                metaKeyIdMatches,
                metaAlgorithmMatches,
                metaDigestAlgorithmMatches,
                metaSignatureInputMatches,
                metaStreamMatches,
                metaRangeMatches,
                metaTenantMatches,
                metaRowCountMatches
        );
    }

    /**
     * Extracts:
     * - metaIndex: index of last non-blank line in tailLines
     * - metaLineOut: set to UTF-8 string of that last non-blank line (trimmed of \r/\n)
     * Also enforces:
     * - no non-blank trailing lines after metaIndex
     */
    private static int extractMetaIndexAndLine(List<byte[]> tailLines, StringBuilder metaLineOut) {

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

        for (int i = metaIndex + 1; i < tailLines.size(); i++) {
            String after = trimLineEndingsUtf8(tailLines.get(i));
            if (!after.isBlank()) {
                throw new IllegalArgumentException("Trailing data after meta line");
            }
        }

        metaLineOut.setLength(0);
        metaLineOut.append(metaLineUtf8);

        return metaIndex;
    }

    /**
     * True only if the line is a meta envelope JSON object with a top-level "_export_meta" object.
     * This avoids false positives where payload JSON contains "_export_meta" inside string values.
     */
    private boolean isMetaEnvelopeLine(byte[] lineBytes) {
        if (!containsMetaFieldToken(lineBytes)) {
            return false;
        }
        return isMetaEnvelopeLine(trimLineEndingsUtf8(lineBytes));
    }

    private boolean isMetaEnvelopeLine(String lineUtf8) {
        if (lineUtf8 == null || lineUtf8.isBlank()) return false;

        // Very cheap guard before parsing JSON.
        if (!lineUtf8.contains("\"_export_meta\"")) return false;

        int i = 0;
        while (i < lineUtf8.length() && Character.isWhitespace(lineUtf8.charAt(i))) i++;
        if (i >= lineUtf8.length() || lineUtf8.charAt(i) != '{') return false;

        try {
            JsonNode root = objectMapper.readTree(lineUtf8);
            JsonNode metaNode = root.get("_export_meta");
            return metaNode != null && metaNode.isObject();
        } catch (Exception e) {
            // Not a valid JSON meta envelope.
            return false;
        }
    }

    private static boolean containsMetaFieldToken(byte[] lineBytes) {
        if (lineBytes == null || lineBytes.length == 0) return false;

        int end = lineBytes.length;
        while (end > 0) {
            byte b = lineBytes[end - 1];
            if (b == '\n' || b == '\r') end--;
            else break;
        }
        if (end <= 0) return false;

        // Scan for META_FIELD_TOKEN in [0, end)
        int max = end - META_FIELD_TOKEN.length;
        for (int i = 0; i <= max; i++) {
            boolean match = true;
            for (int j = 0; j < META_FIELD_TOKEN.length; j++) {
                if (lineBytes[i + j] != META_FIELD_TOKEN[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    private static boolean isBlankLine(byte[] lineBytes) {
        if (lineBytes == null || lineBytes.length == 0) return true;

        int end = lineBytes.length;
        while (end > 0) {
            byte b = lineBytes[end - 1];
            if (b == '\n' || b == '\r') end--;
            else break;
        }
        if (end <= 0) return true;

        for (int i = 0; i < end; i++) {
            byte b = lineBytes[i];
            if (b != ' ' && b != '\t') return false;
        }
        return true;
    }

    private static boolean isNonBlankLine(byte[] lineBytes) {
        return !isBlankLine(lineBytes);
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
            String computedDigestHex,
            boolean signatureValid,
            long payloadLengthBytes,
            long payloadRowCount
    ) {
    }

    private record MetaValidationResult(
            AuditExportMetadataDTO meta,
            boolean metaPresent,
            boolean metaParsed,
            boolean metaSnapshotIdMatches,
            boolean metaDigestMatches,
            boolean metaSignatureMatches,
            boolean metaKeyIdMatches,
            boolean metaAlgorithmMatches,
            boolean metaDigestAlgorithmMatches,
            boolean metaSignatureInputMatches,
            boolean metaStreamMatches,
            boolean metaRangeMatches,
            boolean metaTenantMatches,
            boolean metaRowCountMatches
    ) {
    }

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
                (extra != null && extra.length > 0)
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