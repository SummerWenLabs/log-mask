package io.github.summerwenlabs.log.mask.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.summerwenlabs.log.mask.MaskStrategyRegistry;

/**
 * Immutable compiled query governance rules safe for concurrent reuse.
 */
public final class HttpQueryGovernance {

    private static final HttpQueryGovernance NONE = new HttpQueryGovernance(
            Collections.<RuleKey, CompiledRule>emptyMap());

    private final Map<RuleKey, CompiledRule> rules;

    private HttpQueryGovernance(Map<RuleKey, CompiledRule> rules) {
        this.rules = rules;
    }

    public static HttpQueryGovernance none() {
        return NONE;
    }

    public static HttpQueryGovernance of(
            Iterable<HttpQueryRule> rules,
            MaskStrategyRegistry strategyRegistry) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(strategyRegistry, "strategyRegistry");
        Map<RuleKey, CompiledRule> compiled = new LinkedHashMap<RuleKey, CompiledRule>();
        int index = 0;
        for (HttpQueryRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("Query rule[" + index + "] must not be null");
            }
            RuleKey key = new RuleKey(rule.name(), rule.host());
            CompiledRule previous = compiled.put(
                    key,
                    CompiledRule.compile(rule, strategyRegistry, index));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate query rule scope: name=" + rule.name()
                                + ", host=" + rule.host());
            }
            index++;
        }
        return compiled.isEmpty()
                ? NONE
                : new HttpQueryGovernance(
                        Collections.unmodifiableMap(
                                new LinkedHashMap<RuleKey, CompiledRule>(compiled)));
    }

    QueryValue govern(
            String host,
            String name,
            String value,
            boolean valueDecoded,
            boolean hasValue) {
        CompiledRule rule = find(host, name);
        if (rule == null) {
            return QueryValue.unchanged(value);
        }
        if (rule.excluded) {
            return QueryValue.excluded();
        }
        if (!hasValue) {
            return QueryValue.governed(null, false);
        }
        CompiledHttpValueRule.Result result = !valueDecoded && rule.contentAware
                ? CompiledHttpValueRule.fallback()
                : rule.valueRule.apply(value);
        return QueryValue.governed(result.value(), result.fallbackApplied());
    }

    private CompiledRule find(String host, String name) {
        if (host != null) {
            CompiledRule scoped = rules.get(
                    new RuleKey(name, host.toLowerCase(Locale.ROOT)));
            if (scoped != null) {
                return scoped;
            }
        }
        return rules.get(new RuleKey(name, null));
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
                HttpQueryRule rule,
                MaskStrategyRegistry strategyRegistry,
                int index) {
            boolean excluded = rule.type() == HttpRuleType.EXCLUDE;
            boolean contentAware = rule.typeCode() != null
                    || (rule.type() != HttpRuleType.REDACT && !excluded);
            return new CompiledRule(
                    excluded,
                    contentAware,
                    CompiledHttpValueRule.compile(
                            rule.type(),
                            rule.typeCode(),
                            strategyRegistry,
                            true,
                            "Query rule[" + index + "]"));
        }
    }

    static final class QueryValue {
        final boolean governed;
        final boolean excluded;
        final String value;
        final boolean fallbackApplied;

        private QueryValue(
                boolean governed,
                boolean excluded,
                String value,
                boolean fallbackApplied) {
            this.governed = governed;
            this.excluded = excluded;
            this.value = value;
            this.fallbackApplied = fallbackApplied;
        }

        static QueryValue unchanged(String value) {
            return new QueryValue(false, false, value, false);
        }

        static QueryValue governed(String value, boolean fallbackApplied) {
            return new QueryValue(true, false, value, fallbackApplied);
        }

        static QueryValue excluded() {
            return new QueryValue(true, true, null, false);
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
