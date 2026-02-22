package com.brutecx.docflow_backend.domain.audit.forensic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.HexFormat;

/**
 * Writes JSONL payload lines to response output stream, while computing SHA-256 digest
 * and (optionally) updating a streaming Signature over the EXACT same payload bytes.
 * IMPORTANT INVARIANT:
 * - Digest/signature MUST cover ONLY payload lines
 * - Metadata line is EXCLUDED
 * - Every payload line includes trailing '\n'
 */
@Service
public class DigestingForensicExportService {

    private static final byte[] NL = "\n".getBytes(StandardCharsets.UTF_8);

    /**
     * Dedicated immutable writer derived from Spring-configured ObjectMapper.
     * We do NOT mutate the injected mapper.
     */
    private final ObjectWriter writer;

    public DigestingForensicExportService(ObjectMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("ObjectMapper required");
        }
        this.writer = mapper.writer();
    }

    public static final class ExportDigestContext {

        private final OutputStream rawOut;
        private final OutputStream payloadOut;
        private final MessageDigest payloadDigest;

        private ExportDigestContext(
                OutputStream rawOut,
                OutputStream payloadOut,
                MessageDigest payloadDigest
        ) {
            this.rawOut = rawOut;
            this.payloadOut = payloadOut;
            this.payloadDigest = payloadDigest;
        }

        public void flush() throws IOException {
            rawOut.flush();
        }

        public byte[] finalizePayloadDigest() {
            return payloadDigest.digest();
        }
    }

    /**
     * Starts a streaming export that computes digest + optional signature.
     *
     * @param responseOut response output stream
     * @param signatureOrNull initialized Signature (initSign already called) or null
     */
    public ExportDigestContext beginDigestStream(
            OutputStream responseOut,
            Signature signatureOrNull
    ) {
        try {
            if (responseOut == null) {
                throw new IllegalArgumentException("responseOut required");
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            OutputStream raw = new BufferedOutputStream(responseOut);

            OutputStream payload = new OutputStream() {

                @Override
                public void write(int b) throws IOException {
                    raw.write(b);
                    md.update((byte) b);

                    if (signatureOrNull != null) {
                        try {
                            signatureOrNull.update((byte) b);
                        } catch (Exception e) {
                            throw new IOException("Failed to update payload signature", e);
                        }
                    }
                }

                @Override
                public void write(@Nonnull byte[] b, int off, int len) throws IOException {
                    raw.write(b, off, len);
                    md.update(b, off, len);

                    if (signatureOrNull != null) {
                        try {
                            signatureOrNull.update(b, off, len);
                        } catch (Exception e) {
                            throw new IOException("Failed to update payload signature", e);
                        }
                    }
                }

                @Override
                public void flush() throws IOException {
                    raw.flush();
                }
            };

            return new ExportDigestContext(raw, payload, md);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize digest stream", e);
        }
    }

    /**
     * Writes a payload JSONL line (included in digest/signature).
     */
    public void writePayloadJsonl(Object dto, ExportDigestContext ctx) throws IOException {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx required");
        }
        writeJsonLine(dto, ctx.payloadOut);
    }

    /**
     * Writes metadata JSONL line (EXCLUDED from digest/signature).
     */
    public void writeMetaJsonl(Object envelope, ExportDigestContext ctx) throws IOException {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx required");
        }
        writeJsonLine(envelope, ctx.rawOut);
    }

    private void writeJsonLine(Object value, OutputStream out) throws IOException {
        try {
            byte[] json = writer.writeValueAsBytes(value);
            out.write(json);
            out.write(NL);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to serialize JSONL line", e);
        }
    }

    public static String hexSha256(byte[] sha256DigestBytes) {
        if (sha256DigestBytes == null || sha256DigestBytes.length == 0) {
            throw new IllegalArgumentException("sha256DigestBytes required");
        }
        return HexFormat.of().formatHex(sha256DigestBytes);
    }
}