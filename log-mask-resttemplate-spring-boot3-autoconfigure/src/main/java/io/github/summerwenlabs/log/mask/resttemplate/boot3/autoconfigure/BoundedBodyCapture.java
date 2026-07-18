/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.io.ByteArrayOutputStream;

/**
 * Captures a bounded contiguous prefix of a response stream as it is consumed.
 *
 * <p>The capture never reads from the stream itself. Skips or discontinuous
 * reads make an empty capture unusable, while overlapping reads after reset do
 * not duplicate bytes. Reaching the budget stops capture without limiting the
 * application's response stream.
 *
 * @author SummerWen
 * @since 0.1
 */
final class BoundedBodyCapture {

    private final int maxCapturedBytes;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean limitExceeded;
    private boolean discontinuous;
    private boolean endOfInput;
    private boolean readFailed;

    BoundedBodyCapture(int maxCapturedBytes) {
        this.maxCapturedBytes = maxCapturedBytes;
    }

    void record(long position, int value) {
        if (limitExceeded || discontinuous) {
            return;
        }
        int captured = bytes.size();
        if (position > captured) {
            discontinuous = true;
        } else if (position == captured) {
            if (captured == maxCapturedBytes) {
                limitExceeded = true;
            } else {
                bytes.write(value);
            }
        }
    }

    void record(long position, byte[] source, int offset, int length) {
        if (length <= 0 || limitExceeded || discontinuous) {
            return;
        }
        int captured = bytes.size();
        if (position > captured) {
            discontinuous = true;
            return;
        }
        long overlap = captured - position;
        if (overlap >= length) {
            return;
        }
        int sourceOffset = offset + (int) overlap;
        int remaining = length - (int) overlap;
        int capacity = maxCapturedBytes - captured;
        int copied = Math.min(remaining, Math.max(0, capacity));
        if (copied > 0) {
            bytes.write(source, sourceOffset, copied);
        }
        if (copied < remaining) {
            limitExceeded = true;
        }
    }

    void skipped(long position, long count) {
        if (count <= 0 || discontinuous) {
            return;
        }
        long skippedEnd = position > Long.MAX_VALUE - count
                ? Long.MAX_VALUE
                : position + count;
        if (skippedEnd > bytes.size()) {
            discontinuous = true;
        }
    }

    void endOfInput() {
        endOfInput = true;
    }

    void readFailed() {
        readFailed = true;
        discontinuous = true;
    }

    boolean isLimitExceeded() {
        return limitExceeded;
    }

    boolean hasBytes() {
        return bytes.size() > 0;
    }

    byte[] bytes() {
        return bytes.toByteArray();
    }

    boolean isConfirmedEmpty() {
        return endOfInput
                && bytes.size() == 0
                && !discontinuous
                && !limitExceeded;
    }

    boolean hasNoUsableBytes() {
        return bytes.size() == 0 && (readFailed || discontinuous);
    }
}
