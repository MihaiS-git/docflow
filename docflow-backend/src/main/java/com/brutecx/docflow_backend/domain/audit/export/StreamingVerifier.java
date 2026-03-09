package com.brutecx.docflow_backend.domain.audit.export;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Component
public class StreamingVerifier {

    private static final String DIGEST_ALG = "SHA-256";
    private static final String SIGNATURE_ALG = "SHA256withRSA";
    private static final int MAX_JSONL_LINE_BYTES = 1_048_576;

    private static final HexFormat HEX = HexFormat.of();

    public record StreamingVerifyResult(
            String computedDigestHex,
            boolean signatureValid,
            long payloadLengthBytes,
            long payloadRowCount
    ) {}

    public StreamingVerifyResult verify(
            AuditExportSnapshot snapshot,
            MultipartFile file,
            PublicKey pk
    ) throws Exception {
        MessageDigest md = MessageDigest.getInstance(DIGEST_ALG);

        Signature verifier = Signature.getInstance(SIGNATURE_ALG);
        verifier.initVerify(pk);

        byte[] signatureBytes =
                Base64.getDecoder().decode(snapshot.getSignatureB64());

        ArrayDeque<byte[]> tailLines = new ArrayDeque<>(2);

        final long[] payloadLengthBytes = {0L};
        final long[] payloadRowCount = {0L};

        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            JsonlScanner scanner =
                    new JsonlScanner(in, file.getSize(), MAX_JSONL_LINE_BYTES);

            scanner.scan(line -> {
                tailLines.addLast(line);

                if (tailLines.size() > 2) {
                    byte[] commit = tailLines.removeFirst();

                    if (isBlankLine(commit)) {
                        throw new IllegalArgumentException(
                                "Blank line inside payload");
                    }

                    if (containsMetaToken(commit)) {
                        throw new IllegalArgumentException(
                                "Unexpected _export_meta inside payload");
                    }

                    payloadLengthBytes[0] += commit.length;
                    payloadRowCount[0]++;

                    md.update(commit);
                    verifier.update(commit);
                }
            });
        }

        List<byte[]> tailList = List.copyOf(tailLines);
        int metaIndex = findMetaIndex(tailList);

        for (int i = 0; i < metaIndex; i++) {
            byte[] commit = tailList.get(i);

            if (isBlankLine(commit)) {
                throw new IllegalArgumentException("Blank line inside payload");
            }

            if (containsMetaToken(commit)) {
                throw new IllegalArgumentException(
                        "Unexpected _export_meta inside payload");
            }

            payloadLengthBytes[0] += commit.length;
            payloadRowCount[0]++;

            md.update(commit);
            verifier.update(commit);
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

    private static boolean isBlankLine(byte[] lineBytes) {
        if (lineBytes == null || lineBytes.length == 0) return true;
        int end = lineBytes.length;

        while (end > 0 &&
                (lineBytes[end - 1] == '\n' || lineBytes[end - 1] == '\r')) {
            end--;
        }
        if (end <= 0) return true;
        for (int i = 0; i < end; i++) {
            if (lineBytes[i] != ' ' && lineBytes[i] != '\t') {
                return false;
            }
        }
        return true;
    }

    private static boolean containsMetaToken(byte[] line) {
        byte[] token = "\"_export_meta\"".getBytes(StandardCharsets.UTF_8);
        int end = line.length;

        while (end > 0) {
            byte b = line[end - 1];
            if (b == '\n' || b == '\r') end--;
            else break;
        }

        int max = end - token.length;

        for (int i = 0; i <= max; i++) {
            boolean match = true;
            for (int j = 0; j < token.length; j++) {
                if (line[i + j] != token[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    private static int findMetaIndex(List<byte[]> tailLines) {
        for (int i = tailLines.size() - 1; i >= 0; i--) {
            String s =
                    new String(tailLines.get(i), StandardCharsets.UTF_8).trim();
            if (!s.isBlank()) {
                return i;
            }
        }
        throw new IllegalArgumentException("Missing _export_meta line");
    }
}