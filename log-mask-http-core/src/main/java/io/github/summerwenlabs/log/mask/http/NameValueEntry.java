package io.github.summerwenlabs.log.mask.http;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One ordered name and its ordered values in an HTTP log region.
 */
public final class NameValueEntry {

    private final String name;
    private final List<String> values;

    NameValueEntry(String name, List<String> values) {
        this.name = Objects.requireNonNull(name, "name");
        this.values = Collections.unmodifiableList(values);
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }
}
