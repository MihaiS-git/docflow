package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditExportVerificationResultDTO;
import com.brutecx.docflow_backend.domain.security.AuditSigningKey;
import com.brutecx.docflow_backend.domain.security.AuditSigningKeyResolver;
import com.brutecx.docflow_backend.domain.security.ExportSigningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuditExportVerificationService {

    private static final HexFormat HEX = HexFormat.of();

    private final AuditExportSnapshotRepository snapshotRepository;
    private final ExportSigningService exportSigningService;
    private final AuditSigningKeyResolver keyResolver;
    private final ObjectMapper objectMapper;

    private final long maxUploadBytes;

    public AuditExportVerificationService(
            AuditExportSnapshotRepository snapshotRepository,
            ExportSigningService exportSigningService,
            AuditSigningKeyResolver keyResolver,
            ObjectMapper objectMapper,
            @Value("${docflow.audit.export.verify.max-bytes:268435456}") long maxUploadBytes
    ) {
        this.snapshotRepository = snapshotRepository;
        this.exportSigningService = exportSigningService;
        this.keyResolver = keyResolver;
        this.objectMapper = objectMapper;
        this.maxUploadBytes = maxUploadBytes;
    }

    @Transactional(readOnly = true)
    public AuditExportVerificationResultDTO verifySnapshotFile(UUID snapshotId, MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return fail(snapshotId, "No file provided");
        }
        if (file.getSize() > maxUploadBytes) {
            return fail(snapshotId, "File too large (limit=" + maxUploadBytes + " bytes)");
        }

        AuditExportSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + snapshotId));

        File tmp = null;
        try {
            tmp = Files.createTempFile("audit-export-", ".jsonl").toFile();

            try (InputStream in = file.getInputStream();
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp))) {

                long copied = copyWithLimit(in, out, maxUploadBytes);
                if (copied <= 0) {
                    return fail(snapshotId, "Empty upload");
                }
            }

            MetaLocateResult metaLocate = locateMetaLineStartStrict(tmp);
            if (!metaLocate.metaPresent || metaLocate.metaLineUtf8 == null) {
                return failStrict(snapshot, "Missing required final _export_meta line");
            }

            AuditExportMetadataDTO meta = parseMetaStrict(metaLocate.metaLineUtf8, snapshot);

            // Compute SHA-256 over payload bytes (prefix, excluding meta)
            byte[] computedDigestBytes = sha256FilePrefix(tmp, metaLocate.payloadLengthBytes);
            String computedDigestHex = HEX.formatHex(computedDigestBytes);

            boolean digestMatches = computedDigestHex.equalsIgnoreCase(snapshot.getSha256DigestHex());

            boolean keyIdExists = keyResolver.exists(snapshot.getKeyId());

            // Verify STANDARD signature over payload bytes (prefix, excluding meta)
            boolean signatureValid = false;
            if (keyIdExists) {
                try (InputStream payloadIn = new BufferedInputStream(new FileInputStream(tmp))) {
                    signatureValid = exportSigningService.verifyPayloadSignatureStream(
                            payloadIn,
                            metaLocate.payloadLengthBytes,
                            snapshot.getSignatureB64(),
                            snapshot.getSignatureAlg(),
                            snapshot.getKeyId()
                    );
                }
            }

            boolean fingerprintMatches = false;
            if (keyIdExists) {
                AuditSigningKey keyRow = keyResolver.findById(snapshot.getKeyId()).orElse(null);
                if (keyRow != null && meta.publicKeyFingerprint() != null) {
                    fingerprintMatches = meta.publicKeyFingerprint().equalsIgnoreCase(keyRow.getFingerprintSha256Hex());
                }
            }

            boolean metaPresent = true;
            boolean metaParsed = true;

            boolean metaSnapshotIdMatches = meta.snapshotId() != null && meta.snapshotId().equals(snapshot.getId());
            boolean metaDigestMatches = meta.payloadSha256Hex() != null && meta.payloadSha256Hex().equalsIgnoreCase(snapshot.getSha256DigestHex());

            boolean metaSignatureMatches = safeEq(meta.signatureB64(), snapshot.getSignatureB64());
            boolean metaKeyIdMatches = safeEq(meta.keyId(), snapshot.getKeyId());
            boolean metaAlgorithmMatches = safeEq(meta.signatureAlgorithm(), snapshot.getSignatureAlg());

            boolean metaDigestAlgorithmMatches = safeEq(meta.digestAlgorithm(), AuditExportMetadataDTO.DIGEST_ALG_SHA256);
            boolean metaSignatureInputMatches = safeEq(meta.signatureInput(), AuditExportMetadataDTO.SIGNATURE_INPUT_PAYLOAD_JSONL_BYTES);

            boolean metaStreamMatches = safeEq(meta.stream(), snapshot.getStream());
            boolean metaRangeMatches =
                    safeEq(meta.from(), snapshot.getFromTs()) &&
                            safeEq(meta.to(), snapshot.getToTs());

            boolean metaTenantMatches = safeEq(meta.tenantId(), snapshot.getTenantId());
            boolean metaRowCountMatches = meta.rowCount() == snapshot.getRowCount();

            boolean ok =
                    digestMatches &&
                            keyIdExists &&
                            signatureValid &&
                            fingerprintMatches &&
                            metaPresent &&
                            metaParsed &&
                            metaSnapshotIdMatches &&
                            metaDigestMatches &&
                            metaSignatureMatches &&
                            metaKeyIdMatches &&
                            metaAlgorithmMatches &&
                            metaDigestAlgorithmMatches &&
                            metaSignatureInputMatches &&
                            metaStreamMatches &&
                            metaRangeMatches &&
                            metaTenantMatches &&
                            metaRowCountMatches;

            String message = ok ? "OK" : "FAILED";

            return new AuditExportVerificationResultDTO(
                    ok,
                    snapshot.getId(),
                    snapshot.getStream(),
                    snapshot.getTenantId(),
                    snapshot.getFromTs(),
                    snapshot.getToTs(),
                    snapshot.getRowCount(),
                    snapshot.getSha256DigestHex(),
                    computedDigestHex,
                    digestMatches,
                    keyIdExists,
                    signatureValid && fingerprintMatches,
                    snapshot.getSignatureAlg(),
                    snapshot.getKeyId(),
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
                    metaRowCountMatches,
                    message
            );

        } catch (IllegalArgumentException e) {
            return failStrict(snapshot, e.getMessage());
        } catch (Exception e) {
            return failStrict(snapshot, "Verification failed: " + e.getMessage());
        } finally {
            if (tmp != null) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private AuditExportVerificationResultDTO failStrict(AuditExportSnapshot snapshot, String message) {
        return new AuditExportVerificationResultDTO(
                false,
                snapshot.getId(),
                snapshot.getStream(),
                snapshot.getTenantId(),
                snapshot.getFromTs(),
                snapshot.getToTs(),
                snapshot.getRowCount(),
                snapshot.getSha256DigestHex(),
                null,
                false,
                keyResolver.exists(snapshot.getKeyId()),
                false,
                snapshot.getSignatureAlg(),
                snapshot.getKeyId(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                message
        );
    }

    private static AuditExportVerificationResultDTO fail(UUID snapshotId, String message) {
        return new AuditExportVerificationResultDTO(
                false,
                snapshotId,
                null,
                null,
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
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                message
        );
    }

    private AuditExportMetadataDTO parseMetaStrict(String metaLineUtf8, AuditExportSnapshot snapshot) {
        try {
            JsonNode root = objectMapper.readTree(metaLineUtf8);
            JsonNode metaNode = root.get("_export_meta");
            if (metaNode == null || !metaNode.isObject()) {
                throw new IllegalArgumentException("Invalid meta line: missing _export_meta object");
            }

            AuditExportMetadataDTO meta = objectMapper.treeToValue(metaNode, AuditExportMetadataDTO.class);

            if (meta.snapshotId() == null) throw new IllegalArgumentException("Invalid meta: snapshotId missing");
            if (meta.payloadSha256Hex() == null || meta.payloadSha256Hex().isBlank()) throw new IllegalArgumentException("Invalid meta: payloadSha256Hex missing");
            if (meta.signatureB64() == null || meta.signatureB64().isBlank()) throw new IllegalArgumentException("Invalid meta: signatureB64 missing");
            if (meta.signatureAlgorithm() == null || meta.signatureAlgorithm().isBlank()) throw new IllegalArgumentException("Invalid meta: signatureAlgorithm missing");
            if (meta.keyId() == null || meta.keyId().isBlank()) throw new IllegalArgumentException("Invalid meta: keyId missing");
            if (meta.publicKeyFingerprint() == null || meta.publicKeyFingerprint().isBlank()) throw new IllegalArgumentException("Invalid meta: publicKeyFingerprint missing");
            if (meta.digestAlgorithm() == null || meta.digestAlgorithm().isBlank()) throw new IllegalArgumentException("Invalid meta: digestAlgorithm missing");
            if (meta.signatureInput() == null || meta.signatureInput().isBlank()) throw new IllegalArgumentException("Invalid meta: signatureInput missing");

            if (!meta.snapshotId().equals(snapshot.getId())) {
                throw new IllegalArgumentException("Meta snapshotId does not match registry snapshotId");
            }
            if (!safeEq(meta.stream(), snapshot.getStream())) {
                throw new IllegalArgumentException("Meta stream does not match registry stream");
            }

            // HARD REQUIREMENT: payload-bytes model only
            if (!AuditExportMetadataDTO.SIGNATURE_INPUT_PAYLOAD_JSONL_BYTES.equals(meta.signatureInput())) {
                throw new IllegalArgumentException("Meta signatureInput not supported: " + meta.signatureInput());
            }

            return meta;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse meta line: " + e.getMessage(), e);
        }
    }

    private static long copyWithLimit(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            total += r;
            if (total > limit) throw new IllegalStateException("Upload exceeds limit");
            out.write(buf, 0, r);
        }
        out.flush();
        return total;
    }

    private static MetaLocateResult locateMetaLineStartStrict(File file) throws IOException {
        long len = file.length();
        if (len <= 0) return new MetaLocateResult(false, 0, null);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long end = len;

            while (end > 0) {
                raf.seek(end - 1);
                int b = raf.read();
                if (b == '\n' || b == '\r') end--;
                else break;
            }
            if (end <= 0) return new MetaLocateResult(false, 0, null);

            long start = end - 1;
            while (start >= 0) {
                raf.seek(start);
                int b = raf.read();
                if (b == '\n') {
                    start++;
                    break;
                }
                start--;
            }
            if (start < 0) start = 0;

            int metaLen = (int) (end - start);
            if (metaLen <= 0) return new MetaLocateResult(false, 0, null);

            byte[] lastLineBytes = new byte[metaLen];
            raf.seek(start);
            raf.readFully(lastLineBytes);

            String lastLine = new String(lastLineBytes, StandardCharsets.UTF_8);

            if (!lastLine.contains("\"_export_meta\"")) {
                return new MetaLocateResult(false, 0, null);
            }

            return new MetaLocateResult(true, start, lastLine);
        }
    }

    private static byte[] sha256FilePrefix(File file, long prefixLen) throws IOException {
        if (prefixLen < 0) throw new IllegalArgumentException("prefixLen < 0");
        if (prefixLen == 0) return sha256(new byte[0]);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[64 * 1024];
            long remaining = prefixLen;
            while (remaining > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (r == -1) break;
                md.update(buf, 0, r);
                remaining -= r;
            }
            if (remaining != 0) {
                throw new IllegalStateException("File shorter than payload length");
            }
        }

        return md.digest();
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean safeEq(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    private record MetaLocateResult(boolean metaPresent, long payloadLengthBytes, String metaLineUtf8) {}
}