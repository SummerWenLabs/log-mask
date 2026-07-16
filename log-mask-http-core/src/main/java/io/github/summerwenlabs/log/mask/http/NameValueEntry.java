package io.github.summerwenlabs.log.mask.http;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Holds one immutable name and its ordered values in an HTTP log region.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class NameValueEntry {

    private final String name;
    private final List<String> values;

    NameValueEntry(String name, List<String> values) {
        this.name = Objects.requireNonNull(name, "name");
        this.values = Collections.unmodifiableList(values);
    }

    /**
     * Return the exact retained name.
     * @return the {@code non-null} name
     */
    public String getName() {
        return name;
    }

    /**
     * Return values in occurrence order.
     * @return an unmodifiable list that may contain {@code null}
     */
    public List<String> getValues() {
        return values;
    }
}
