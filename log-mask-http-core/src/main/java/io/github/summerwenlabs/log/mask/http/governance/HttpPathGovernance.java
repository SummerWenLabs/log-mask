/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.governance;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;

/**
 * Applies immutable compiled governance rules to raw URI path segments.
 *
 * <p>Rules are selected deterministically by host/method scope and then literal
 * segment priority. Named variables are decoded as strict UTF-8 before masking
 * and re-encoded after masking; wildcard segments preserve their raw encoding.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpPathGovernance {

    private static final HttpPathGovernance NONE =
            new HttpPathGovernance(Collections.<CompiledRule>emptyList());

    private final List<CompiledRule> rules;

    private HttpPathGovernance(List<CompiledRule> rules) {
        this.rules = rules;
    }

    /**
     * Return shared governance with no explicit path rules.
     * @return an immutable no-rule instance
     */
    public static HttpPathGovernance none() {
        return NONE;
    }

    /**
     * Compile and validate path rules for concurrent reuse.
     * @param rules declarations in diagnostic order
     * @param strategyRegistry custom content strategies
     * @return immutable compiled governance
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if declarations are invalid or ambiguous
     */
    public static HttpPathGovernance of(
            Iterable<HttpPathRule> rules,
            MaskStrategyRegistry strategyRegistry) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(strategyRegistry, "strategyRegistry");
        List<CompiledRule> compiled = new ArrayList<CompiledRule>();
        int index = 0;
        for (HttpPathRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("Path rule[" + index + "] must not be null");
            }
            compiled.add(CompiledRule.compile(rule, strategyRegistry, index));
            index++;
        }
        validateConflicts(compiled);
        return compiled.isEmpty()
                ? NONE
                : new HttpPathGovernance(
                        Collections.unmodifiableList(new ArrayList<CompiledRule>(compiled)));
    }

    /**
     * Govern a URI path without explicit query rules.
     * @param requestUri URI whose raw path is observed
     * @param method non-empty HTTP token, matched case-insensitively
     * @return the immutable governed URI representation
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code method} is not an HTTP token
     */
    public HttpRequestUri govern(URI requestUri, String method) {
        return govern(requestUri, method, HttpQueryGovernance.none());
    }

    /**
     * Govern a URI path and query with independently compiled rule sets.
     * @param requestUri URI whose raw path and query are observed
     * @param method non-empty HTTP token, matched case-insensitively
     * @param queryGovernance independently compiled query rules
     * @return the immutable governed URI representation
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code method} is not an HTTP token
     */
    public HttpRequestUri govern(
            URI requestUri,
            String method,
            HttpQueryGovernance queryGovernance) {
        Objects.requireNonNull(requestUri, "requestUri");
        Objects.requireNonNull(queryGovernance, "queryGovernance");
        String normalizedMethod = normalizeMethod(method);
        String rawPath = requestUri.getRawPath();
        if (rawPath == null) {
            rawPath = "";
        }
        String[] pathSegments = pathSegments(rawPath);
        String host = requestUri.getHost();
        if (host != null) {
            host = host.toLowerCase(Locale.ROOT);
        }
        CompiledRule selected = select(host, normalizedMethod, pathSegments);
        if (selected == null) {
            return HttpRequestUri.from(requestUri, queryGovernance);
        }
        PathResult result = selected.govern(pathSegments, rawPath.length());
        return HttpRequestUri.fromGovernedPath(
                requestUri,
                queryGovernance,
                result.path,
                result.fallbackApplied);
    }

    private CompiledRule select(String host, String method, String[] pathSegments) {
        if (pathSegments == null) {
            return null;
        }
        CompiledRule selected = null;
        for (CompiledRule candidate : rules) {
            if (!candidate.matches(host, method, pathSegments)) {
                continue;
            }
            if (selected == null || compare(candidate, selected) > 0) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static String[] pathSegments(String rawPath) {
        return rawPath.isEmpty() || rawPath.charAt(0) != '/'
                ? null
                : rawPath.substring(1).split("/", -1);
    }

    private static int compare(CompiledRule left, CompiledRule right) {
        int scope = left.scopeSize() - right.scopeSize();
        if (scope != 0) {
            return scope;
        }
        for (int index = 0; index < left.tokens.size(); index++) {
            boolean leftLiteral = left.tokens.get(index).literal();
            boolean rightLiteral = right.tokens.get(index).literal();
            if (leftLiteral != rightLiteral) {
                return leftLiteral ? 1 : -1;
            }
        }
        return 0;
    }

    private static void validateConflicts(List<CompiledRule> rules) {
        for (int left = 0; left < rules.size(); left++) {
            for (int right = left + 1; right < rules.size(); right++) {
                CompiledRule first = rules.get(left);
                CompiledRule second = rules.get(right);
                if (first.hasEquivalentOverlappingTemplate(second)
                        && first.scopeOverlaps(second)
                        && !first.hasNarrowerScopeThan(second)
                        && !second.hasNarrowerScopeThan(first)) {
                    throw new IllegalArgumentException(
                            "Path rules[" + first.index + "] and [" + second.index
                                    + "] conflict: equivalent overlapping templates"
                                    + " have no unique scope or literal priority");
                }
            }
        }
    }

    private static String normalizeMethod(String method) {
        Objects.requireNonNull(method, "method");
        if (method.isEmpty()
                || HttpPathRule.hasSurroundingWhitespace(method)
                || !HttpPathRule.isHttpToken(method)) {
            throw new IllegalArgumentException(
                    "HTTP method must be non-empty and have no surrounding whitespace");
        }
        return method.toUpperCase(Locale.ROOT);
    }

    private static final class CompiledRule {
        private final int index;
        private final String host;
        private final String method;
        private final List<Token> tokens;

        private CompiledRule(
                int index,
                String host,
                String method,
                List<Token> tokens) {
            this.index = index;
            this.host = host;
            this.method = method;
            this.tokens = tokens;
        }

        static CompiledRule compile(
                HttpPathRule rule,
                MaskStrategyRegistry strategyRegistry,
                int index) {
            List<Token> tokens = parsePattern(rule.pattern(), index);
            Set<String> templateVariables = new LinkedHashSet<String>();
            for (Token token : tokens) {
                if (token.kind == TokenKind.VARIABLE) {
                    templateVariables.add(token.text);
                }
            }

            Map<String, HttpPathRule.VariableDeclaration> declarations =
                    new LinkedHashMap<String, HttpPathRule.VariableDeclaration>();
            for (HttpPathRule.VariableDeclaration declaration : rule.variables()) {
                String name = declaration.name();
                if (name == null || name.isEmpty()) {
                    throw validation(index, "variable name must be non-empty");
                }
                if (declarations.put(name, declaration) != null) {
                    throw validation(
                            index,
                            "variable '" + name + "' must be configured exactly once");
                }
            }
            if (!templateVariables.equals(declarations.keySet())) {
                Set<String> missing = new LinkedHashSet<String>(templateVariables);
                missing.removeAll(declarations.keySet());
                Set<String> extra = new LinkedHashSet<String>(declarations.keySet());
                extra.removeAll(templateVariables);
                throw validation(
                        index,
                        "variable declarations must exactly match the template"
                                + "; missing=" + missing + ", extra=" + extra);
            }

            Map<String, CompiledHttpValueRule> valueRules =
                    new LinkedHashMap<String, CompiledHttpValueRule>();
            for (Map.Entry<String, HttpPathRule.VariableDeclaration> entry
                    : declarations.entrySet()) {
                HttpPathRule.VariableDeclaration declaration = entry.getValue();
                valueRules.put(
                        entry.getKey(),
                        CompiledHttpValueRule.compile(
                                declaration.type(),
                                declaration.typeCode(),
                                strategyRegistry,
                                false,
                                "Path rule[" + index + "] variable '" + entry.getKey() + "'"));
            }
            List<Token> compiledTokens = new ArrayList<Token>(tokens.size());
            for (Token token : tokens) {
                compiledTokens.add(token.kind == TokenKind.VARIABLE
                        ? token.withRule(valueRules.get(token.text))
                        : token);
            }
            return new CompiledRule(
                    index,
                    rule.host(),
                    rule.method(),
                    Collections.unmodifiableList(compiledTokens));
        }

        private static List<Token> parsePattern(String pattern, int index) {
            if (pattern.isEmpty() || pattern.charAt(0) != '/') {
                throw validation(index, "pattern must be an absolute path starting with '/'");
            }
            if (pattern.indexOf('?') >= 0 || pattern.indexOf('#') >= 0) {
                throw validation(index, "pattern must not contain query or fragment syntax");
            }
            if (pattern.contains("**")) {
                throw validation(index, "pattern does not support '**'");
            }
            String[] segments = pattern.substring(1).split("/", -1);
            List<Token> tokens = new ArrayList<Token>(segments.length);
            for (String segment : segments) {
                if ("*".equals(segment)) {
                    tokens.add(Token.wildcard());
                } else if (segment.startsWith("{") && segment.endsWith("}")) {
                    String name = segment.substring(1, segment.length() - 1);
                    if (name.isEmpty()
                            || name.indexOf('{') >= 0
                            || name.indexOf('}') >= 0
                            || name.indexOf('*') >= 0) {
                        throw validation(index, "pattern contains an invalid variable segment");
                    }
                    tokens.add(Token.variable(name));
                } else {
                    if (segment.indexOf('*') >= 0
                            || segment.indexOf('{') >= 0
                            || segment.indexOf('}') >= 0
                            || !hasValidPercentEncoding(segment)) {
                        throw validation(index, "pattern contains an invalid literal segment");
                    }
                    tokens.add(Token.literal(segment));
                }
            }
            return tokens;
        }

        private static boolean hasValidPercentEncoding(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (value.charAt(index) != '%') {
                    continue;
                }
                if (index + 2 >= value.length()
                        || Character.digit(value.charAt(index + 1), 16) < 0
                        || Character.digit(value.charAt(index + 2), 16) < 0) {
                    return false;
                }
                index += 2;
            }
            return true;
        }

        boolean matches(
                String requestHost,
                String requestMethod,
                String[] pathSegments) {
            if (host != null && !host.equals(requestHost)) {
                return false;
            }
            if (method != null && !method.equals(requestMethod)) {
                return false;
            }
            if (pathSegments.length != tokens.size()) {
                return false;
            }
            for (int index = 0; index < tokens.size(); index++) {
                Token token = tokens.get(index);
                if (token.literal() && !token.text.equals(pathSegments[index])) {
                    return false;
                }
            }
            return true;
        }

        PathResult govern(String[] pathSegments, int rawPathLength) {
            StringBuilder governed = new StringBuilder(rawPathLength);
            boolean fallbackApplied = false;
            for (int index = 0; index < tokens.size(); index++) {
                governed.append('/');
                Token token = tokens.get(index);
                if (token.kind != TokenKind.VARIABLE) {
                    governed.append(pathSegments[index]);
                    continue;
                }
                CompiledHttpValueRule.Result result;
                if (!token.rule.observesValue()) {
                    result = token.rule.apply(null);
                } else {
                    UriPathCodec.Decoded decoded = UriPathCodec.decode(pathSegments[index]);
                    result = decoded.successful()
                            ? token.rule.apply(decoded.value())
                            : CompiledHttpValueRule.fallback();
                }
                governed.append(UriPathCodec.encode(result.value()));
                fallbackApplied |= result.fallbackApplied();
            }
            return new PathResult(governed.toString(), fallbackApplied);
        }

        int scopeSize() {
            return (host == null ? 0 : 1) + (method == null ? 0 : 1);
        }

        boolean hasEquivalentOverlappingTemplate(CompiledRule other) {
            if (tokens.size() != other.tokens.size()) {
                return false;
            }
            for (int index = 0; index < tokens.size(); index++) {
                Token first = tokens.get(index);
                Token second = other.tokens.get(index);
                if (first.literal() != second.literal()) {
                    return false;
                }
                if (first.literal() && !first.text.equals(second.text)) {
                    return false;
                }
            }
            return true;
        }

        boolean scopeOverlaps(CompiledRule other) {
            return overlaps(host, other.host) && overlaps(method, other.method);
        }

        boolean hasNarrowerScopeThan(CompiledRule other) {
            boolean contained = containsScopeValue(host, other.host)
                    && containsScopeValue(method, other.method);
            return contained && scopeSize() > other.scopeSize();
        }

        private static boolean overlaps(String first, String second) {
            return first == null || second == null || first.equals(second);
        }

        private static boolean containsScopeValue(String narrower, String broader) {
            return broader == null || broader.equals(narrower);
        }

        private static IllegalArgumentException validation(int index, String reason) {
            return new IllegalArgumentException("Path rule[" + index + "]: " + reason);
        }
    }

    private enum TokenKind {
        LITERAL,
        WILDCARD,
        VARIABLE
    }

    private static final class Token {
        private final TokenKind kind;
        private final String text;
        private final CompiledHttpValueRule rule;

        private Token(TokenKind kind, String text, CompiledHttpValueRule rule) {
            this.kind = kind;
            this.text = text;
            this.rule = rule;
        }

        static Token literal(String value) {
            return new Token(TokenKind.LITERAL, value, null);
        }

        static Token wildcard() {
            return new Token(TokenKind.WILDCARD, null, null);
        }

        static Token variable(String name) {
            return new Token(TokenKind.VARIABLE, name, null);
        }

        Token withRule(CompiledHttpValueRule rule) {
            return new Token(kind, text, rule);
        }

        boolean literal() {
            return kind == TokenKind.LITERAL;
        }
    }

    private static final class PathResult {
        private final String path;
        private final boolean fallbackApplied;

        private PathResult(String path, boolean fallbackApplied) {
            this.path = path;
            this.fallbackApplied = fallbackApplied;
        }
    }
}
