package com.brutecx.docflow_backend.domain.audit.export;

import java.util.Arrays;

public class ByteArrayOutputStreamEx {
    private byte[] buf;
    private int count;

    ByteArrayOutputStreamEx(int initialCapacity) {
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
