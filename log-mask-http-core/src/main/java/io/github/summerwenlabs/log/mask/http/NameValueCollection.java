package io.github.summerwenlabs.log.mask.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable name/value entries ordered by first name occurrence.
 */
public final class NameValueCollection {

    private final List<NameValueEntry> entries;

    private NameValueCollection(List<NameValueEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<NameValueEntry> getEntries() {
        return entries;
    }

    public static final class Builder {
        private final Map<String, List<String>> valuesByName =
                new LinkedHashMap<String, List<String>>();

        private Builder() {
        }

        public Builder add(String name, String value) {
            valuesFor(name).add(value);
            return this;
        }

        public Builder addAll(String name, Iterable<String> values) {
            Objects.requireNonNull(values, "values");
            List<String> target = valuesFor(name);
            for (String value : values) {
                target.add(value);
            }
            return this;
        }

        public NameValueCollection build() {
            List<NameValueEntry> entries = new ArrayList<NameValueEntry>(valuesByName.size());
            for (Map.Entry<String, List<String>> entry : valuesByName.entrySet()) {
                entries.add(new NameValueEntry(
                        entry.getKey(),
                        new ArrayList<String>(entry.getValue())));
            }
            return new NameValueCollection(entries);
        }

        private List<String> valuesFor(String name) {
            Objects.requireNonNull(name, "name");
            List<String> values = valuesByName.get(name);
            if (values == null) {
                values = new ArrayList<String>();
                valuesByName.put(name, values);
            }
            return values;
        }
    }
}
