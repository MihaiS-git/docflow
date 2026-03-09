package com.brutecx.docflow_backend.domain.audit.export;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportMetadataDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;


@Component
public class JsonlMetaExtractor {

    private static final int MAX_JSONL_LINE_BYTES = 1_048_576;
    private static final byte[] META_FIELD_TOKEN = "\"_export_meta\"".getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    public JsonlMetaExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MetaValidationResult extractAndValidateMeta(AuditExportSnapshot snapshot, MultipartFile file) throws Exception {
        TailMetaScan scan = scanTailAndCountMetaEnvelopes(file);

        if (scan.metaEnvelopeCount() == 0) {
            throw new IllegalArgumentException("Missing _export_meta line");
        }
        if (scan.metaEnvelopeCount() > 1) {
            throw new IllegalArgumentException("Multiple _export_meta lines");
        }

        List<byte[]> tailList = new ArrayList<>(scan.tailLines());
        String metaLineUtf8 = extractMetaLine(tailList);

        return parseAndValidateMetaStrict(metaLineUtf8, snapshot);
    }

    public TailMetaScan scanTailAndCountMetaEnvelopes(MultipartFile file) throws Exception {
        ArrayDeque<byte[]> tailLines = new ArrayDeque<>(2);

        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            JsonlScanner scanner = new JsonlScanner(in, file.getSize(), MAX_JSONL_LINE_BYTES);

            scanner.scan(line -> {
                tailLines.addLast(line);
                if (tailLines.size() > 2) tailLines.removeFirst();
            });
        }

        int metaEnvelopeCount;

        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            JsonlScanner scanner = new JsonlScanner(in, file.getSize(), MAX_JSONL_LINE_BYTES);
            final int[] counted = {0};
            scanner.scan(line -> {
                if (isNonBlankLine(line) && isMetaEnvelopeLine(line)) {
                    counted[0]++;
                }
            });
            metaEnvelopeCount = counted[0];
        }

        return new TailMetaScan(new ArrayList<>(tailLines), metaEnvelopeCount);
    }

    private String extractMetaLine(List<byte[]> tailLines) {

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

        return metaLineUtf8;
    }

    private MetaValidationResult parseAndValidateMetaStrict(String metaLineUtf8, AuditExportSnapshot snapshot) throws Exception {
        JsonNode root = objectMapper.readTree(metaLineUtf8);
        JsonNode metaNode = root.get("_export_meta");
        if (metaNode == null || !metaNode.isObject()) {
            throw new IllegalArgumentException("Invalid meta line");
        }

        AuditExportMetadataDTO meta = objectMapper.treeToValue(metaNode, AuditExportMetadataDTO.class);

        boolean metaSnapshotIdMatches = meta.snapshotId() != null && meta.snapshotId().equals(snapshot.getId());
        boolean metaKeyIdMatches = meta.keyId() == null || snapshot.getKeyId() == null || meta.keyId().trim().equals(snapshot.getKeyId().trim());
        boolean metaAlgorithmMatches = meta.signatureAlgorithm() == null || snapshot.getSignatureAlg() == null || meta.signatureAlgorithm().trim().equals(snapshot.getSignatureAlg().trim());
        boolean metaSignatureMatches = meta.signatureB64() == null || snapshot.getSignatureB64() == null || meta.signatureB64().trim().equals(snapshot.getSignatureB64().trim());
        boolean metaDigestMatches = meta.payloadSha256Hex() == null || snapshot.getSha256DigestHex() == null || meta.payloadSha256Hex().trim().equalsIgnoreCase(snapshot.getSha256DigestHex().trim());
        boolean metaDigestAlgorithmMatches = meta.digestAlgorithm() == null || "SHA-256".equals(meta.digestAlgorithm().trim());
        boolean metaSignatureInputMatches =
                meta.signatureInput() == null ||
                        AuditExportMetadataDTO.SIGNATURE_INPUT_PAYLOAD_BYTES.equals(meta.signatureInput().trim());

        return new MetaValidationResult(
                meta,
                true,
                true,
                metaSnapshotIdMatches,
                metaDigestMatches,
                metaSignatureMatches,
                metaKeyIdMatches,
                metaAlgorithmMatches,
                metaDigestAlgorithmMatches,
                metaSignatureInputMatches,
                true,
                true,
                true,
                true
        );
    }

    private boolean isMetaEnvelopeLine(byte[] lineBytes) {
        return containsMetaFieldToken(lineBytes) && isMetaEnvelopeLine(trimLineEndingsUtf8(lineBytes));
    }

    private boolean isMetaEnvelopeLine(String lineUtf8) {
        if (lineUtf8 == null || lineUtf8.isBlank()) return false;
        if (!lineUtf8.contains("\"_export_meta\"")) return false;

        int i = 0;
        while (i < lineUtf8.length() && Character.isWhitespace(lineUtf8.charAt(i))) i++;
        if (i >= lineUtf8.length() || lineUtf8.charAt(i) != '{') return false;

        try {
            JsonNode root = objectMapper.readTree(lineUtf8);
            JsonNode metaNode = root.get("_export_meta");
            return metaNode != null && metaNode.isObject();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsMetaFieldToken(byte[] lineBytes) {
        if (lineBytes == null || lineBytes.length == 0) return false;

        int end = lineBytes.length;
        while (end > 0 && (lineBytes[end - 1] == '\n' || lineBytes[end - 1] == '\r')) end--;
        if (end <= 0) return false;

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

    private boolean isNonBlankLine(byte[] lineBytes) {
        return !isBlankLine(lineBytes);
    }

    private boolean isBlankLine(byte[] lineBytes) {
        if (lineBytes == null || lineBytes.length == 0) return true;
        int end = lineBytes.length;
        while (end > 0 && (lineBytes[end - 1] == '\n' || lineBytes[end - 1] == '\r')) end--;
        if (end <= 0) return true;

        for (int i = 0; i < end; i++) if (lineBytes[i] != ' ' && lineBytes[i] != '\t') return false;
        return true;
    }

    private static String trimLineEndingsUtf8(byte[] lineBytes) {
        if (lineBytes == null || lineBytes.length == 0) return "";
        int end = lineBytes.length;
        while (end > 0 && (lineBytes[end - 1] == '\n' || lineBytes[end - 1] == '\r')) end--;
        if (end <= 0) return "";
        return new String(lineBytes, 0, end, StandardCharsets.UTF_8);
    }

    public record TailMetaScan(List<byte[]> tailLines, int metaEnvelopeCount) {
    }

    public record MetaValidationResult(
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
}