package io.github.summerwenlabs.log.mask.http;

import java.util.Locale;

/**
 * One explicit header governance rule with an optional host scope.
 */
public final class HttpHeaderRule {

    private final String name;
    private final String host;
    private final HttpRuleType type;
    private final String typeCode;

    private HttpHeaderRule(Builder builder) {
        if (builder.name == null
                || builder.name.isEmpty()
                || hasSurroundingWhitespace(builder.name)) {
            throw new IllegalArgumentException(
                    "Header rule name must be non-empty and have no surrounding whitespace");
        }
        if ((builder.type == null) == (builder.typeCode == null)) {
            throw new IllegalArgumentException(
                    "Header rule must configure exactly one of type or typeCode");
        }
        if (builder.typeCode != null
                && (builder.typeCode.isEmpty()
                        || hasSurroundingWhitespace(builder.typeCode))) {
            throw new IllegalArgumentException(
                    "Header rule typeCode must be non-empty and have no surrounding whitespace");
        }
        this.name = builder.name.toLowerCase(Locale.ROOT);
        this.host = normalizeHost(builder.host);
        this.type = builder.type;
        this.typeCode = builder.typeCode;
    }

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
                    "Header rule host must be non-empty and have no surrounding whitespace");
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

    public static final class Builder {
        private String name;
        private String host;
        private HttpRuleType type;
        private String typeCode;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder type(HttpRuleType type) {
            this.type = type;
            return this;
        }

        public Builder typeCode(String typeCode) {
            this.typeCode = typeCode;
            return this;
        }

        public HttpHeaderRule build() {
            return new HttpHeaderRule(this);
        }
    }
}
