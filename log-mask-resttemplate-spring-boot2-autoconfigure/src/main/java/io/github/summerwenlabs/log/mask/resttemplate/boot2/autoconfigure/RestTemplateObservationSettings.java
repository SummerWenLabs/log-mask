/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.github.summerwenlabs.log.mask.http.exchange.NameValueShape;
import io.github.summerwenlabs.log.mask.http.governance.HttpHeaderGovernance;
import io.github.summerwenlabs.log.mask.http.governance.HttpHeaderRule;
import io.github.summerwenlabs.log.mask.http.governance.HttpPathGovernance;
import io.github.summerwenlabs.log.mask.http.governance.HttpPathRule;
import io.github.summerwenlabs.log.mask.http.governance.HttpQueryGovernance;
import io.github.summerwenlabs.log.mask.http.governance.HttpQueryRule;
import io.github.summerwenlabs.log.mask.http.governance.HttpRuleType;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;

/**
 * Compiles Spring-bound properties into immutable runtime configuration.
 *
 * <p>Body budget, trace keys, custom strategy codes, rule modes, and
 * conflicting scopes are validated once during startup. Runtime exchange
 * handling therefore consumes only thread-safe compiled governance objects.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RestTemplateObservationSettings {

    private static final long MAX_BODY_BYTES = Integer.MAX_VALUE;

    private final boolean governanceEnabled;
    private final int maxBodyBytes;
    private final NameValueShape nameValueShape;
    private final boolean uriDetailsEnabled;
    private final boolean requestHeadersEnabled;
    private final boolean requestBodyEnabled;
    private final boolean responseHeadersEnabled;
    private final boolean responseBodyEnabled;
    private final boolean traceIdEnabled;
    private final List<String> traceIdMdcKeys;
    private final MaskStrategyRegistry strategyRegistry;
    private final HttpPathGovernance pathGovernance;
    private final HttpQueryGovernance queryGovernance;
    private final HttpHeaderGovernance requestHeaderGovernance;
    private final HttpHeaderGovernance responseHeaderGovernance;

    private RestTemplateObservationSettings(
            boolean governanceEnabled,
            int maxBodyBytes,
            NameValueShape nameValueShape,
            boolean uriDetailsEnabled,
            boolean requestHeadersEnabled,
            boolean requestBodyEnabled,
            boolean responseHeadersEnabled,
            boolean responseBodyEnabled,
            boolean traceIdEnabled,
            List<String> traceIdMdcKeys,
            MaskStrategyRegistry strategyRegistry,
            HttpPathGovernance pathGovernance,
            HttpQueryGovernance queryGovernance,
            HttpHeaderGovernance requestHeaderGovernance,
            HttpHeaderGovernance responseHeaderGovernance) {
        this.governanceEnabled = governanceEnabled;
        this.maxBodyBytes = maxBodyBytes;
        this.nameValueShape = nameValueShape;
        this.uriDetailsEnabled = uriDetailsEnabled;
        this.requestHeadersEnabled = requestHeadersEnabled;
        this.requestBodyEnabled = requestBodyEnabled;
        this.responseHeadersEnabled = responseHeadersEnabled;
        this.responseBodyEnabled = responseBodyEnabled;
        this.traceIdEnabled = traceIdEnabled;
        this.traceIdMdcKeys = traceIdMdcKeys;
        this.strategyRegistry = strategyRegistry;
        this.pathGovernance = pathGovernance;
        this.queryGovernance = queryGovernance;
        this.requestHeaderGovernance = requestHeaderGovernance;
        this.responseHeaderGovernance = responseHeaderGovernance;
    }

    static RestTemplateObservationSettings create(
            RestTemplateObservationProperties observation,
            LogMaskGovernanceProperties governance,
            Iterable<? extends MaskTypeDefinition> definitions) {
        if (observation == null) {
            throw new IllegalArgumentException("RestTemplate observation configuration is required");
        }
        if (governance == null) {
            throw new IllegalArgumentException("Log-mask governance configuration is required");
        }
        int maxBodyBytes = bodyBudget(observation);
        NameValueShape nameValueShape = observation.getNameValueShape();
        if (nameValueShape == null) {
            throw new IllegalArgumentException(
                    "log-mask.logging.rest-template.name-value-shape is required");
        }
        List<String> traceIdMdcKeys = traceIdMdcKeys(observation.getTraceId());
        MaskStrategyRegistry strategyRegistry = MaskStrategyRegistry.of(definitions);
        LogMaskGovernanceProperties.Http http = governance.getHttp();
        HttpPathGovernance pathGovernance = HttpPathGovernance.of(
                pathRules(http.getPath().getRules()), strategyRegistry);
        HttpQueryGovernance queryGovernance = HttpQueryGovernance.of(
                queryRules(http.getQuery().getRules()), strategyRegistry);
        HttpHeaderGovernance requestHeaderGovernance = HttpHeaderGovernance.of(
                headerRules(http.getHeaders().getRequest().getRules()), strategyRegistry);
        HttpHeaderGovernance responseHeaderGovernance = HttpHeaderGovernance.of(
                headerRules(http.getHeaders().getResponse().getRules()), strategyRegistry);
        return new RestTemplateObservationSettings(
                governance.isEnabled(),
                maxBodyBytes,
                nameValueShape,
                observation.getUri().isDetailsEnabled(),
                observation.getRequest().isHeadersEnabled(),
                observation.getRequest().isBodyEnabled(),
                observation.getResponse().isHeadersEnabled(),
                observation.getResponse().isBodyEnabled(),
                observation.getTraceId().isEnabled(),
                traceIdMdcKeys,
                strategyRegistry,
                pathGovernance,
                queryGovernance,
                requestHeaderGovernance,
                responseHeaderGovernance);
    }

    static RestTemplateObservationSettings defaults(boolean governanceEnabled) {
        LogMaskGovernanceProperties governance = new LogMaskGovernanceProperties();
        governance.setEnabled(governanceEnabled);
        return create(
                new RestTemplateObservationProperties(),
                governance,
                Collections.<MaskTypeDefinition>emptyList());
    }

    boolean isGovernanceEnabled() {
        return governanceEnabled;
    }

    int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    NameValueShape getNameValueShape() {
        return nameValueShape;
    }

    boolean isUriDetailsEnabled() {
        return uriDetailsEnabled;
    }

    boolean isRequestHeadersEnabled() {
        return requestHeadersEnabled;
    }

    boolean isRequestBodyEnabled() {
        return requestBodyEnabled;
    }

    boolean isResponseHeadersEnabled() {
        return responseHeadersEnabled;
    }

    boolean isResponseBodyEnabled() {
        return responseBodyEnabled;
    }

    boolean isTraceIdEnabled() {
        return traceIdEnabled;
    }

    List<String> getTraceIdMdcKeys() {
        return traceIdMdcKeys;
    }

    MaskStrategyRegistry getStrategyRegistry() {
        return strategyRegistry;
    }

    HttpPathGovernance getPathGovernance() {
        return pathGovernance;
    }

    HttpQueryGovernance getQueryGovernance() {
        return queryGovernance;
    }

    HttpHeaderGovernance getRequestHeaderGovernance() {
        return requestHeaderGovernance;
    }

    HttpHeaderGovernance getResponseHeaderGovernance() {
        return responseHeaderGovernance;
    }

    private static int bodyBudget(RestTemplateObservationProperties observation) {
        if (observation.getMaxBodySize() == null) {
            throw new IllegalArgumentException(
                    "log-mask.logging.rest-template.max-body-size is required");
        }
        long bytes = observation.getMaxBodySize().toBytes();
        if (bytes <= 0L || bytes > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "log-mask.logging.rest-template.max-body-size must be a positive bounded "
                            + "DataSize no larger than " + MAX_BODY_BYTES + " bytes");
        }
        return (int) bytes;
    }

    private static List<String> traceIdMdcKeys(
            RestTemplateObservationProperties.TraceId traceId) {
        if (traceId == null) {
            throw new IllegalArgumentException(
                    "log-mask.logging.rest-template.trace-id configuration is required");
        }
        List<String> configured = traceId.getMdcKeys();
        if (!traceId.isEnabled()) {
            return Collections.emptyList();
        }
        if (configured == null || configured.isEmpty()) {
            throw new IllegalArgumentException(
                    "log-mask.logging.rest-template.trace-id.mdc-keys must not be empty "
                            + "when trace-id is enabled");
        }
        List<String> keys = new ArrayList<String>(configured.size());
        for (String key : configured) {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "log-mask.logging.rest-template.trace-id.mdc-keys must contain "
                                + "only non-blank values");
            }
            keys.add(key);
        }
        return Collections.unmodifiableList(keys);
    }

    private static List<HttpPathRule> pathRules(
            List<LogMaskGovernanceProperties.PathRule> configured) {
        List<HttpPathRule> rules = new ArrayList<HttpPathRule>();
        for (LogMaskGovernanceProperties.PathRule source : requiredRules(
                configured, "log-mask.governance.http.path.rules")) {
            HttpPathRule.Builder rule = HttpPathRule.builder()
                    .pattern(source.getPattern())
                    .host(source.getHost())
                    .method(source.getMethod());
            List<LogMaskGovernanceProperties.PathVariable> variables = source.getVariables();
            if (variables == null) {
                throw new IllegalArgumentException(
                        "Path rule variables must be configured as a list, not null");
            }
            for (LogMaskGovernanceProperties.PathVariable variable : variables) {
                if (variable == null) {
                    throw new IllegalArgumentException("Path rule variables must not contain null");
                }
                HttpRuleType type = ruleType(variable.getType(), "Path variable");
                if (type == null) {
                    rule.variableTypeCode(variable.getName(), variable.getTypeCode());
                } else if (variable.getTypeCode() == null) {
                    rule.variable(variable.getName(), type);
                } else {
                    throw new IllegalArgumentException(
                            "Path variable must configure exactly one of type or type-code");
                }
            }
            rules.add(rule.build());
        }
        return rules;
    }

    private static List<HttpQueryRule> queryRules(
            List<LogMaskGovernanceProperties.ValueRule> configured) {
        List<HttpQueryRule> rules = new ArrayList<HttpQueryRule>();
        for (LogMaskGovernanceProperties.ValueRule source : requiredRules(
                configured, "log-mask.governance.http.query.rules")) {
            HttpQueryRule.Builder rule = HttpQueryRule.builder()
                    .name(source.getName())
                    .host(source.getHost());
            apply(rule, source, "Query rule");
            rules.add(rule.build());
        }
        return rules;
    }

    private static List<HttpHeaderRule> headerRules(
            List<LogMaskGovernanceProperties.ValueRule> configured) {
        List<HttpHeaderRule> rules = new ArrayList<HttpHeaderRule>();
        for (LogMaskGovernanceProperties.ValueRule source : requiredRules(
                configured, "log-mask.governance.http.headers rules")) {
            HttpHeaderRule.Builder rule = HttpHeaderRule.builder()
                    .name(source.getName())
                    .host(source.getHost());
            apply(rule, source, "Header rule");
            rules.add(rule.build());
        }
        return rules;
    }

    private static void apply(
            HttpQueryRule.Builder target,
            LogMaskGovernanceProperties.ValueRule source,
            String sourceName) {
        HttpRuleType type = ruleType(source.getType(), sourceName);
        if (type == null) {
            target.typeCode(source.getTypeCode());
        } else if (source.getTypeCode() == null) {
            target.type(type);
        } else {
            throw new IllegalArgumentException(
                    sourceName + " must configure exactly one of type or type-code");
        }
    }

    private static void apply(
            HttpHeaderRule.Builder target,
            LogMaskGovernanceProperties.ValueRule source,
            String sourceName) {
        HttpRuleType type = ruleType(source.getType(), sourceName);
        if (type == null) {
            target.typeCode(source.getTypeCode());
        } else if (source.getTypeCode() == null) {
            target.type(type);
        } else {
            throw new IllegalArgumentException(
                    sourceName + " must configure exactly one of type or type-code");
        }
    }

    private static HttpRuleType ruleType(String value, String sourceName) {
        if (value == null) {
            return null;
        }
        try {
            return HttpRuleType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(sourceName + " type is invalid: " + value, exception);
        }
    }

    private static <T> List<T> requiredRules(List<T> rules, String path) {
        if (rules == null) {
            throw new IllegalArgumentException(path + " must not be null");
        }
        return rules;
    }
}
