package io.github.summerwenlabs.log.mask;

import java.util.Objects;

/**
 * Result of generating a safe JSON representation within a UTF-8 byte limit.
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
     * Returns whether generation stopped because the configured UTF-8 byte limit was exceeded.
     */
    public boolean isLimitExceeded() {
        return json == null;
    }

    /**
     * Returns the complete JSON representation.
     *
     * @throws IllegalStateException if generation exceeded the byte limit
     */
    public String getJson() {
        if (isLimitExceeded()) {
            throw new IllegalStateException("No JSON is available when the UTF-8 byte limit is exceeded");
        }
        return json;
    }
}
