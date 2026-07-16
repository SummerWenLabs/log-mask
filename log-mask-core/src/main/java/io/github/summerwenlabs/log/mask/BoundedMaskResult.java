package io.github.summerwenlabs.log.mask;

import java.util.Objects;

/**
 * Immutable result of generating safe JSON within a UTF-8 byte limit.
 *
 * <p>A result contains either one complete JSON document or the
 * limit-exceeded state. Partial JSON is never exposed.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class BoundedMaskResult {

    private static final BoundedMaskResult LIMIT_EXCEEDED = new BoundedMaskResult(null);

    private final String json;

    private BoundedMaskResult(String json) {
        this.json = json;
    }

    static BoundedMaskResult complete(String json) {
        return new BoundedMaskResult(Objects.requireNonNull(json, "json"));
    }

    static BoundedMaskResult limitExceeded() {
        return LIMIT_EXCEEDED;
    }

    /**
     * Determine whether generation stopped because the UTF-8 byte limit was
     * exceeded.
     * @return {@code true} when no complete JSON representation is available
     */
    public boolean isLimitExceeded() {
        return json == null;
    }

    /**
     * Return the complete JSON representation.
     * @return the complete JSON document; never {@code null}
     * @throws IllegalStateException if generation exceeded the byte limit
     */
    public String getJson() {
        if (isLimitExceeded()) {
            throw new IllegalStateException("No JSON is available when the UTF-8 byte limit is exceeded");
        }
        return json;
    }
}
