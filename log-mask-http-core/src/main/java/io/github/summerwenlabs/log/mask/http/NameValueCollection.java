package io.github.summerwenlabs.log.mask.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds immutable name/value entries ordered by first name occurrence.
 *
 * <p>Repeated names are merged while preserving value order. Names are kept
 * exactly as supplied and values may be {@code null}.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class NameValueCollection {

    private final List<NameValueEntry> entries;

    private NameValueCollection(List<NameValueEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    /**
     * Create a builder that preserves first-name and per-name value order.
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return entries in first-name occurrence order.
     * @return an unmodifiable entry list
     */
    public List<NameValueEntry> getEntries() {
        return entries;
    }

    /**
     * Collects repeated names into an immutable ordered representation.
     */
    public static final class Builder {
        private final Map<String, List<String>> valuesByName =
                new LinkedHashMap<String, List<String>>();

        private Builder() {
        }

        /**
         * Add one value after existing values for the same exact name.
         * @param name {@code non-null} name, retained exactly
         * @param value value to append; may be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code name} is {@code null}
         */
        public Builder add(String name, String value) {
            valuesFor(name).add(value);
            return this;
        }

        /**
         * Add values in iteration order after existing values for the name.
         * @param name {@code non-null} name, retained exactly
         * @param values values to append; individual values may be {@code null}
         * @return this builder
         * @throws NullPointerException if {@code name} or {@code values} is
         * {@code null}
         */
        public Builder addAll(String name, Iterable<String> values) {
            Objects.requireNonNull(values, "values");
            List<String> target = valuesFor(name);
            for (String value : values) {
                target.add(value);
            }
            return this;
        }

        /**
         * Build an immutable snapshot of all accumulated names and values.
         * @return a new immutable collection
         */
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
