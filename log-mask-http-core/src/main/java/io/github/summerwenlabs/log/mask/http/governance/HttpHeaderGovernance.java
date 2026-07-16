package io.github.summerwenlabs.log.mask.http.governance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.summerwenlabs.log.mask.http.NameValueCollection;
import io.github.summerwenlabs.log.mask.http.NameValueEntry;
import io.github.summerwenlabs.log.mask.http.RegionState;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;

/**
 * Applies immutable compiled governance rules to HTTP header collections.
 *
 * <p>Header names and host scopes are case-insensitive. A host-scoped rule wins
 * over a global rule for the same name. Unmatched values remain unchanged and
 * still produce {@link RegionState#SUCCESS}.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpHeaderGovernance {

    private static final HttpHeaderGovernance NONE = new HttpHeaderGovernance(
            Collections.<RuleKey, CompiledRule>emptyMap());

    private final Map<RuleKey, CompiledRule> rules;

    private HttpHeaderGovernance(Map<RuleKey, CompiledRule> rules) {
        this.rules = rules;
    }

    /**
     * Return shared governance with no explicit header rules.
     * @return an immutable no-rule instance
     */
    public static HttpHeaderGovernance none() {
        return NONE;
    }

    /**
     * Compile and validate header rules for concurrent reuse.
     * @param rules declarations in diagnostic order
     * @param strategyRegistry custom content strategies
     * @return immutable compiled governance
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if a rule is {@code null}, invalid, or
     * duplicated
     */
    public static HttpHeaderGovernance of(
            Iterable<HttpHeaderRule> rules,
            MaskStrategyRegistry strategyRegistry) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(strategyRegistry, "strategyRegistry");
        Map<RuleKey, CompiledRule> compiled = new LinkedHashMap<RuleKey, CompiledRule>();
        int index = 0;
        for (HttpHeaderRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("Header rule[" + index + "] must not be null");
            }
            RuleKey key = new RuleKey(rule.name(), rule.host());
            CompiledRule previous = compiled.put(
                    key,
                    CompiledRule.compile(rule, strategyRegistry, index));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate header rule scope: name=" + rule.name()
                                + ", host=" + rule.host());
            }
            index++;
        }
        return compiled.isEmpty()
                ? NONE
                : new HttpHeaderGovernance(
                        Collections.unmodifiableMap(
                                new LinkedHashMap<RuleKey, CompiledRule>(compiled)));
    }

    /**
     * Govern one request or response header snapshot.
     * @param host effective request host; may be {@code null}
     * @param headers ordered header snapshot
     * @return governed headers and their final execution state
     * @throws NullPointerException if {@code headers} is {@code null}
     */
    public Result govern(String host, NameValueCollection headers) {
        Objects.requireNonNull(headers, "headers");
        String normalizedHost = host == null ? null : host.toLowerCase(Locale.ROOT);
        NameValueCollection.Builder governed = NameValueCollection.builder();
        boolean fallbackApplied = false;
        List<NameValueEntry> entries = headers.getEntries();
        for (NameValueEntry entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ROOT);
            CompiledRule rule = find(normalizedHost, name);
            if (rule == null) {
                governed.addAll(name, entry.getValues());
                continue;
            }
            if (rule.excluded) {
                continue;
            }
            governed.addAll(name, Collections.<String>emptyList());
            for (String value : entry.getValues()) {
                if (value == null && rule.contentAware) {
                    governed.add(name, null);
                    continue;
                }
                CompiledHttpValueRule.Result masked = rule.valueRule.apply(value);
                governed.add(name, masked.value());
                fallbackApplied |= masked.fallbackApplied();
            }
        }
        return new Result(
                fallbackApplied ? RegionState.FALLBACK_APPLIED : RegionState.SUCCESS,
                governed.build());
    }

    private CompiledRule find(String host, String name) {
        if (host != null) {
            CompiledRule scoped = rules.get(new RuleKey(name, host));
            if (scoped != null) {
                return scoped;
            }
        }
        return rules.get(new RuleKey(name, null));
    }

    /**
     * Pairs the final immutable header collection with its execution state.
     */
    public static final class Result {
        private final RegionState state;
        private final NameValueCollection headers;

        private Result(RegionState state, NameValueCollection headers) {
            this.state = state;
            this.headers = headers;
        }

        /**
         * Return the final header execution state.
         * @return the result state
         */
        public RegionState getState() {
            return state;
        }

        /**
         * Return the immutable governed headers.
         * @return the governed header collection
         */
        public NameValueCollection getHeaders() {
            return headers;
        }
    }

    private static final class CompiledRule {
        private final boolean excluded;
        private final boolean contentAware;
        private final CompiledHttpValueRule valueRule;

        private CompiledRule(
                boolean excluded,
                boolean contentAware,
                CompiledHttpValueRule valueRule) {
            this.excluded = excluded;
            this.contentAware = contentAware;
            this.valueRule = valueRule;
        }

        private static CompiledRule compile(
                HttpHeaderRule rule,
                MaskStrategyRegistry strategyRegistry,
                int index) {
            boolean excluded = rule.type() == HttpRuleType.EXCLUDE;
            return new CompiledRule(
                    excluded,
                    rule.type() != HttpRuleType.REDACT && !excluded,
                    CompiledHttpValueRule.compile(
                            rule.type(),
                            rule.typeCode(),
                            strategyRegistry,
                            true,
                            "Header rule[" + index + "]"));
        }
    }

    private static final class RuleKey {
        private final String name;
        private final String host;

        private RuleKey(String name, String host) {
            this.name = name;
            this.host = host;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RuleKey)) {
                return false;
            }
            RuleKey that = (RuleKey) other;
            return name.equals(that.name) && Objects.equals(host, that.host);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, host);
        }
    }
}
