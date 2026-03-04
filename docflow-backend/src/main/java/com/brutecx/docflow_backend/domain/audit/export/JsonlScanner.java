package com.brutecx.docflow_backend.domain.audit.export;

import java.io.InputStream;

public class JsonlScanner {
    private final InputStream in;
    private final long maxUploadBytes;
    private final int maxLineBytes;

    private long totalRead = 0L;

    private final ByteArrayOutputStreamEx currentLine =
            new ByteArrayOutputStreamEx(16 * 1024);

    JsonlScanner(InputStream in, long maxUploadBytes, int maxLineBytes) {
        this.in = in;
        this.maxUploadBytes = maxUploadBytes;
        this.maxLineBytes = maxLineBytes;
    }

    interface LineConsumer {
        void accept(byte[] line) throws Exception;
    }

    void scan(LineConsumer consumer) throws Exception {
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

                if (currentLine.size() > maxLineBytes) {
                    throw new IllegalArgumentException("JSONL line exceeds limit");
                }

                if (b == '\n') {
                    consumer.accept(currentLine.toByteArrayAndReset());
                }
            }
        }

        if (currentLine.size() > 0) {
            consumer.accept(currentLine.toByteArrayAndReset());
        }
    }
}
