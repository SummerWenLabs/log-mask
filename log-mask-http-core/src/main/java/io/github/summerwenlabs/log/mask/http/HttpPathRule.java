package io.github.summerwenlabs.log.mask.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One named path-template governance rule with optional host and method scopes.
 */
public final class HttpPathRule {

    private final String pattern;
    private final String host;
    private final String method;
    private final List<VariableDeclaration> variables;

    private HttpPathRule(Builder builder) {
        if (builder.pattern == null) {
            throw new IllegalArgumentException("Path rule pattern is required");
        }
        this.pattern = builder.pattern;
        this.host = normalize(builder.host, false, "host");
        this.method = normalize(builder.method, true, "method");
        this.variables = Collections.unmodifiableList(
                new ArrayList<VariableDeclaration>(builder.variables));
    }

    public static Builder builder() {
        return new Builder();
    }

    String pattern() {
        return pattern;
    }

    String host() {
        return host;
    }

    String method() {
        return method;
    }

    List<VariableDeclaration> variables() {
        return variables;
    }

    private static String normalize(
            String value,
            boolean uppercase,
            String fieldName) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty() || hasSurroundingWhitespace(value)) {
            throw new IllegalArgumentException(
                    "Path rule " + fieldName
                            + " must be non-empty and have no surrounding whitespace");
        }
        if (uppercase ? !isHttpToken(value) : !isHost(value)) {
            throw new IllegalArgumentException("Path rule " + fieldName + " is invalid: " + value);
        }
        return uppercase
                ? value.toUpperCase(Locale.ROOT)
                : value.toLowerCase(Locale.ROOT);
    }

    static boolean hasSurroundingWhitespace(String value) {
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        return isWhitespace(first) || isWhitespace(last);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isHost(String value) {
        if (value.indexOf('/') >= 0
                || value.indexOf('?') >= 0
                || value.indexOf('#') >= 0
                || value.indexOf('@') >= 0
                || value.indexOf('*') >= 0) {
            return false;
        }
        int firstColon = value.indexOf(':');
        if (firstColon < 0) {
            return true;
        }
        return value.startsWith("[")
                && value.endsWith("]")
                && firstColon > 1
                && firstColon < value.length() - 1;
    }

    static boolean isHttpToken(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || "!#$%&'*+-.^_`|~".indexOf(character) >= 0) {
                continue;
            }
            return false;
        }
        return true;
    }

    static final class VariableDeclaration {
        private final String name;
        private final HttpRuleType type;
        private final String typeCode;

        private VariableDeclaration(String name, HttpRuleType type, String typeCode) {
            this.name = name;
            this.type = type;
            this.typeCode = typeCode;
        }

        String name() {
            return name;
        }

        HttpRuleType type() {
            return type;
        }

        String typeCode() {
            return typeCode;
        }
    }

    public static final class Builder {
        private String pattern;
        private String host;
        private String method;
        private final List<VariableDeclaration> variables =
                new ArrayList<VariableDeclaration>();

        private Builder() {
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder variable(String name, HttpRuleType type) {
            variables.add(new VariableDeclaration(name, type, null));
            return this;
        }

        public Builder variableTypeCode(String name, String typeCode) {
            variables.add(new VariableDeclaration(name, null, typeCode));
            return this;
        }

        public HttpPathRule build() {
            return new HttpPathRule(this);
        }
    }
}
