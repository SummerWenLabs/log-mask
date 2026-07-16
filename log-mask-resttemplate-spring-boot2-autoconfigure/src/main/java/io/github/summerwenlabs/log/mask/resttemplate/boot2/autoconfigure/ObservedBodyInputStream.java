/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.io.InputStream;

/**
 * Tees bytes into a bounded capture while delegating every stream operation.
 *
 * <p>The wrapper never pre-reads, retries, or closes independently. Reads,
 * skips, marks, resets, and failures are tracked only to determine whether the
 * bytes consumed by the application form a usable log view.
 *
 * @author SummerWen
 * @since 0.1
 */
final class ObservedBodyInputStream extends InputStream {

    private final InputStream delegate;
    private final BoundedBodyCapture capture;
    private long position;
    private long markedPosition;

    ObservedBodyInputStream(InputStream delegate, BoundedBodyCapture capture) {
        this.delegate = delegate;
        this.capture = capture;
    }

    @Override
    public int read() throws IOException {
        try {
            int value = delegate.read();
            if (value < 0) {
                capture.endOfInput();
            } else {
                capture.record(position, value);
                position++;
            }
            return value;
        } catch (IOException | RuntimeException | Error failure) {
            capture.readFailed();
            throw failure;
        }
    }

    @Override
    public int read(byte[] destination, int offset, int length) throws IOException {
        try {
            int read = delegate.read(destination, offset, length);
            if (read < 0) {
                capture.endOfInput();
            } else if (read > 0) {
                capture.record(position, destination, offset, read);
                position += read;
            }
            return read;
        } catch (IOException | RuntimeException | Error failure) {
            capture.readFailed();
            throw failure;
        }
    }

    @Override
    public long skip(long count) throws IOException {
        try {
            long skipped = delegate.skip(count);
            if (skipped > 0) {
                capture.skipped(position, skipped);
                position = position > Long.MAX_VALUE - skipped
                        ? Long.MAX_VALUE
                        : position + skipped;
            }
            return skipped;
        } catch (IOException | RuntimeException | Error failure) {
            capture.readFailed();
            throw failure;
        }
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public synchronized void mark(int readLimit) {
        delegate.mark(readLimit);
        markedPosition = position;
    }

    @Override
    public synchronized void reset() throws IOException {
        delegate.reset();
        position = markedPosition;
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }
}
