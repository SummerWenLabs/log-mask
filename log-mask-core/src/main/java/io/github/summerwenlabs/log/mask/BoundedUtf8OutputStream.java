package io.github.summerwenlabs.log.mask;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class BoundedUtf8OutputStream extends OutputStream {

    private final int maxBytes;
    private final IOException limitExceeded = new IOException("UTF-8 JSON byte limit exceeded");

    private byte[] buffer;
    private int size;
    private boolean exceeded;
    private boolean discarding;

    BoundedUtf8OutputStream(int maxBytes) {
        this.maxBytes = maxBytes;
        this.buffer = new byte[Math.min(maxBytes, 1024)];
    }

    @Override
    public void write(int value) throws IOException {
        if (discarding) {
            return;
        }
        ensureWithinLimit(1);
        ensureCapacity(size + 1);
        buffer[size++] = (byte) value;
    }

    @Override
    public void write(byte[] values, int offset, int length) throws IOException {
        if (values == null) {
            throw new NullPointerException("values");
        }
        if ((offset | length) < 0 || length > values.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        if (discarding) {
            return;
        }
        ensureWithinLimit(length);
        ensureCapacity(size + length);
        System.arraycopy(values, offset, buffer, size, length);
        size += length;
    }

    boolean isLimitExceeded() {
        return exceeded;
    }

    void discardFurtherWrites() {
        discarding = true;
    }

    String toUtf8String() {
        return new String(buffer, 0, size, StandardCharsets.UTF_8);
    }

    void ensureWithinLimit(int additionalBytes) throws IOException {
        if (exceeded || additionalBytes > maxBytes - size) {
            exceeded = true;
            throw limitExceeded;
        }
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= buffer.length) {
            return;
        }
        int doubledCapacity = buffer.length <= maxBytes / 2
                ? buffer.length * 2
                : maxBytes;
        buffer = Arrays.copyOf(buffer, Math.max(requiredCapacity, doubledCapacity));
    }
}
