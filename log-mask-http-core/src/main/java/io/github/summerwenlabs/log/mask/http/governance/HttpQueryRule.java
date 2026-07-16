/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.governance;

import java.util.Locale;

/**
 * Declares one explicit query governance rule with an optional host scope.
 *
 * <p>Query names are matched exactly and hosts are normalized to lower case.
 * Exactly one built-in type or custom type code is required.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpQueryRule {

    private final String name;
    private final String host;
    private final HttpRuleType type;
    private final String typeCode;

    private HttpQueryRule(Builder builder) {
        if (builder.name == null) {
            throw new IllegalArgumentException("Query rule name is required");
        }
        if ((builder.type == null) == (builder.typeCode == null)) {
            throw new IllegalArgumentException(
                    "Query rule must configure exactly one of type or typeCode");
        }
        if (builder.typeCode != null
                && (builder.typeCode.isEmpty()
                        || hasSurroundingWhitespace(builder.typeCode))) {
            throw new IllegalArgumentException(
                    "Query rule typeCode must be non-empty"
                            + " and have no surrounding whitespace");
        }
        this.name = builder.name;
        this.host = normalizeHost(builder.host);
        this.type = builder.type;
        this.typeCode = builder.typeCode;
    }

    /**
     * Create a builder for one query rule.
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    String name() {
        return name;
    }

    String host() {
        return host;
    }

    HttpRuleType type() {
        return type;
    }

    String typeCode() {
        return typeCode;
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        if (host.isEmpty() || hasSurroundingWhitespace(host)) {
            throw new IllegalArgumentException(
                    "Query rule host must be non-empty and have no surrounding whitespace");
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static boolean hasSurroundingWhitespace(String value) {
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        return isWhitespace(first) || isWhitespace(last);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /**
     * Builds a query rule with an exact name and optional host scope.
     *
     * <p>Exactly one of {@link #type(HttpRuleType)} or
     * {@link #typeCode(String)} must be called.
     */
    public static final class Builder {
        private String name;
        private String host;
        private HttpRuleType type;
        private String typeCode;

        private Builder() {
        }

        /**
         * Set the exact query name to match.
         * @param name query name; validated when the rule is built
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set an optional case-insensitive host scope.
         * @param host host scope, or {@code null} for global scope
         * @return this builder
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Set one built-in HTTP rule type.
         * @param type built-in type
         * @return this builder
         */
        public Builder type(HttpRuleType type) {
            this.type = type;
            return this;
        }

        /**
         * Set one exact custom strategy code.
         * @param typeCode custom code
         * @return this builder
         */
        public Builder typeCode(String typeCode) {
            this.typeCode = typeCode;
            return this;
        }

        /**
         * Build and validate the immutable query rule.
         * @return a new rule
         * @throws IllegalArgumentException if required values or mode are
         * invalid
         */
        public HttpQueryRule build() {
            return new HttpQueryRule(this);
        }
    }
}
