package io.github.summerwenlabs.log.mask.http;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import io.github.summerwenlabs.log.mask.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.MaskTypeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPathGovernanceTest {

    @Test
    void governsNamedSegmentsAndKeepsWildcardSegmentsWithoutChangingTheRequest() {
        URI requestUri = URI.create(
                "HTTPS://API.Example.COM/users/13800138000/orders/a%2fb?tag=java#details");
        HttpPathGovernance governance = HttpPathGovernance.of(
                Collections.singletonList(
                        HttpPathRule.builder()
                                .pattern("/users/{phone}/orders/*")
                                .variable("phone", HttpRuleType.PHONE)
                                .build()),
                MaskStrategyRegistry.empty());

        HttpRequestUri result = governance.govern(requestUri, "GET");

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                "https://api.example.com/users/138%2A%2A%2A%2A8000/orders/a%2fb?tag=java",
                result.getFull());
        assertEquals(
                "/users/138%2A%2A%2A%2A8000/orders/a%2fb",
                result.getPath());
        assertEquals(
                "HTTPS://API.Example.COM/users/13800138000/orders/a%2fb?tag=java#details",
                requestUri.toString());
    }

    @Test
    void rejectsInvalidTemplatesVariablesAndScopesWhenRulesAreCompiled() {
        IllegalArgumentException doubleWildcard = assertThrows(
                IllegalArgumentException.class,
                () -> compile(HttpPathRule.builder()
                        .pattern("/users/**")
                        .build()));
        assertTrue(doubleWildcard.getMessage().contains("rule[0]"));
        assertTrue(doubleWildcard.getMessage().contains("**"));

        assertThrows(
                IllegalArgumentException.class,
                () -> compile(HttpPathRule.builder()
                        .pattern("/users/{id}")
                        .build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> compile(HttpPathRule.builder()
                        .pattern("/users/*")
                        .variable("id", HttpRuleType.REDACT)
                        .build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> compile(HttpPathRule.builder()
                        .pattern("/users/{id}")
                        .variable("id", HttpRuleType.REDACT)
                        .variableTypeCode("id", "custom")
                        .build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> compile(HttpPathRule.builder()
                        .pattern("/users/{id}")
                        .variable("id", HttpRuleType.EXCLUDE)
                        .build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> compile(HttpPathRule.builder()
                        .pattern("/users/{id}")
                        .variableTypeCode("id", "missing")
                        .build()));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpPathRule.builder()
                        .pattern("/users/*")
                        .host("https://api.example.com:443")
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpPathRule.builder()
                        .pattern("/users/*")
                        .host("*.example.com")
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpPathRule.builder()
                        .pattern("/users/*")
                        .method("GE T")
                        .build());
    }

    @Test
    void encodesStrategyResultsAndFallsBackOnlyForVariablesThatCannotBeGoverned() {
        URI requestUri = URI.create(
                "https://api.example.com/values/%E4%B8%AD/%FF/secret/null-result?keep=%2f");
        MaskStrategyRegistry registry = MaskStrategyRegistry.of(Arrays.asList(
                definition("reserved", "/%?#\u4E2D"),
                failingDefinition("failed"),
                definition("null-result", null)));
        HttpPathGovernance governance = HttpPathGovernance.of(
                Collections.singletonList(
                        HttpPathRule.builder()
                                .pattern("/values/{encoded}/{badUtf8}/{failed}/{nullResult}")
                                .variableTypeCode("encoded", "reserved")
                                .variableTypeCode("badUtf8", "reserved")
                                .variableTypeCode("failed", "failed")
                                .variableTypeCode("nullResult", "null-result")
                                .build()),
                registry);

        HttpRequestUri result = governance.govern(requestUri, "GET");

        assertEquals(RegionState.FALLBACK_APPLIED, result.getState());
        assertEquals(
                "/values/%2F%25%3F%23%E4%B8%AD"
                        + "/%3Credacted%3E/%3Credacted%3E/%3Credacted%3E",
                result.getPath());
        assertEquals(
                "https://api.example.com/values/%2F%25%3F%23%E4%B8%AD"
                        + "/%3Credacted%3E/%3Credacted%3E/%3Credacted%3E?keep=%2f",
                result.getFull());
        assertEquals(
                "https://api.example.com/values/%E4%B8%AD/%FF/secret/null-result?keep=%2f",
                requestUri.toString());
    }

    @Test
    void treatsRedactionMalformedBuiltInsAndCustomCodesAsSuccessfulGovernance() {
        HttpPathGovernance governance = HttpPathGovernance.of(
                Collections.singletonList(
                        HttpPathRule.builder()
                                .pattern("/values/{builtIn}/{custom}/{shortValue}/{secret}")
                                .variable("builtIn", HttpRuleType.PHONE)
                                .variableTypeCode("custom", "PHONE")
                                .variable("shortValue", HttpRuleType.PHONE)
                                .variable("secret", HttpRuleType.REDACT)
                                .build()),
                MaskStrategyRegistry.of(
                        Collections.singletonList(definition("PHONE", "custom/phone"))));

        HttpRequestUri result = governance.govern(
                URI.create("https://example.com/values/13800138000/13800138000/12/%FF"),
                "get");

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                "/values/138%2A%2A%2A%2A8000/custom%2Fphone"
                        + "/%2A%2A/%3Credacted%3E",
                result.getPath());
    }

    @Test
    void selectsByNarrowerScopeThenLeftmostLiteralRegardlessOfRuleOrder() {
        HttpPathRule literalPrefix = HttpPathRule.builder()
                .pattern("/users/admin/{section}/{item}")
                .variable("section", HttpRuleType.REDACT)
                .variable("item", HttpRuleType.REDACT)
                .build();
        HttpPathRule hostSpecific = HttpPathRule.builder()
                .pattern("/users/{id}/fixed/detail")
                .host("API.EXAMPLE.COM")
                .variable("id", HttpRuleType.FULL)
                .build();
        URI requestUri = URI.create("https://api.example.com/users/admin/fixed/detail");

        HttpRequestUri forward = pathGovernance(literalPrefix, hostSpecific)
                .govern(requestUri, "GET");
        HttpRequestUri reverse = pathGovernance(hostSpecific, literalPrefix)
                .govern(requestUri, "GET");
        HttpRequestUri literalWinner = pathGovernance(literalPrefix, hostSpecific)
                .govern(
                        URI.create("https://other.example.com/users/admin/fixed/detail"),
                        "GET");

        assertEquals(
                "/users/%2A%2A%2A%2A%2A/fixed/detail",
                forward.getPath());
        assertEquals(forward.getPath(), reverse.getPath());
        assertEquals(
                "/users/admin/%3Credacted%3E/%3Credacted%3E",
                literalWinner.getPath());
    }

    @Test
    void rejectsOnlyEquivalentRulesWithOverlappingIncomparableScopes() {
        HttpPathRule hostOnly = HttpPathRule.builder()
                .pattern("/users/{id}")
                .host("api.example.com")
                .variable("id", HttpRuleType.REDACT)
                .build();
        HttpPathRule methodOnly = HttpPathRule.builder()
                .pattern("/users/*")
                .method("GET")
                .build();

        IllegalArgumentException conflict = assertThrows(
                IllegalArgumentException.class,
                () -> pathGovernance(hostOnly, methodOnly));

        assertTrue(conflict.getMessage().contains("rules[0]"));
        assertTrue(conflict.getMessage().contains("[1]"));
        assertTrue(conflict.getMessage().contains("equivalent overlapping"));
        assertThrows(
                IllegalArgumentException.class,
                () -> pathGovernance(
                        HttpPathRule.builder()
                                .pattern("/users/{id}")
                                .variable("id", HttpRuleType.REDACT)
                                .build(),
                        HttpPathRule.builder()
                                .pattern("/users/{phone}")
                                .variable("phone", HttpRuleType.PHONE)
                                .build()));
        assertDoesNotThrow(
                () -> pathGovernance(
                        HttpPathRule.builder()
                                .pattern("/users/{id}")
                                .variable("id", HttpRuleType.REDACT)
                                .build(),
                        HttpPathRule.builder()
                                .pattern("/users/{id}")
                                .host("api.example.com")
                                .variable("id", HttpRuleType.FULL)
                                .build(),
                        HttpPathRule.builder()
                                .pattern("/users/{id}")
                                .host("other.example.com")
                                .variable("id", HttpRuleType.PHONE)
                                .build()));
    }

    @Test
    void preservesUnmatchedPathsAndKeepsOverallUriFailureAsTheFinalState() {
        HttpPathGovernance governance = compile(HttpPathRule.builder()
                .pattern("/users/{id}")
                .variable("id", HttpRuleType.FULL)
                .build());

        HttpRequestUri tooShort = governance.govern(
                URI.create("https://example.com/users"),
                "GET");
        HttpRequestUri tooLong = governance.govern(
                URI.create("https://example.com/users/a%2fb/"),
                "GET");
        HttpRequestUri invalidAuthority = governance.govern(
                URI.create("http://foo_bar/users/%FF?keep=value"),
                "GET");

        assertEquals(RegionState.SUCCESS, tooShort.getState());
        assertEquals("/users", tooShort.getPath());
        assertEquals(RegionState.SUCCESS, tooLong.getState());
        assertEquals("/users/a%2fb/", tooLong.getPath());
        assertEquals(RegionState.PROCESSING_FAILED, invalidAuthority.getState());
        assertEquals("/users/%3Credacted%3E", invalidAuthority.getPath());
        assertEquals(
                "http://foo_bar/users/%3Credacted%3E?keep=value",
                invalidAuthority.getFull());
    }

    @Test
    void decodesVariablesBeforeGovernanceAndReencodesTheResultAsOneSegment() {
        HttpPathGovernance governance = HttpPathGovernance.of(
                Collections.singletonList(
                        HttpPathRule.builder()
                                .pattern("/values/{value}/tail")
                                .variableTypeCode("value", "identity")
                                .build()),
                MaskStrategyRegistry.of(
                        Collections.singletonList(identityDefinition("identity"))));

        HttpRequestUri result = governance.govern(
                URI.create("https://example.com/values/%2f%25%3f%23%e4%b8%ad/tail"),
                "GET");

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                "/values/%2F%25%3F%23%E4%B8%AD/tail",
                result.getPath());
    }

    @Test
    void prefersHostAndMethodScopeOverHostScopeAndTheGlobalFallback() {
        HttpPathGovernance governance = pathGovernance(
                HttpPathRule.builder()
                        .pattern("/ids/{id}")
                        .variable("id", HttpRuleType.REDACT)
                        .build(),
                HttpPathRule.builder()
                        .pattern("/ids/{id}")
                        .host("api.example.com")
                        .variable("id", HttpRuleType.PHONE)
                        .build(),
                HttpPathRule.builder()
                        .pattern("/ids/{id}")
                        .host("API.EXAMPLE.COM")
                        .method("post")
                        .variable("id", HttpRuleType.FULL)
                        .build());

        HttpRequestUri global = governance.govern(
                URI.create("https://other.example.com/ids/13800138000"),
                "POST");
        HttpRequestUri host = governance.govern(
                URI.create("https://api.example.com/ids/13800138000"),
                "GET");
        HttpRequestUri hostAndMethod = governance.govern(
                URI.create("https://api.example.com/ids/13800138000"),
                "post");

        assertEquals("/ids/%3Credacted%3E", global.getPath());
        assertEquals("/ids/138%2A%2A%2A%2A8000", host.getPath());
        assertEquals(
                "/ids/%2A%2A%2A%2A%2A%2A%2A%2A%2A%2A%2A",
                hostAndMethod.getPath());
    }

    private static HttpPathGovernance compile(HttpPathRule rule) {
        return HttpPathGovernance.of(
                Collections.singletonList(rule),
                MaskStrategyRegistry.empty());
    }

    private static HttpPathGovernance pathGovernance(HttpPathRule... rules) {
        return HttpPathGovernance.of(Arrays.asList(rules), MaskStrategyRegistry.empty());
    }

    private static MaskTypeDefinition definition(final String code, final String result) {
        return new MaskTypeDefinition() {
            @Override
            public String getTypeCode() {
                return code;
            }

            @Override
            public String mask(String value) {
                return result;
            }
        };
    }

    private static MaskTypeDefinition failingDefinition(final String code) {
        return new MaskTypeDefinition() {
            @Override
            public String getTypeCode() {
                return code;
            }

            @Override
            public String mask(String value) {
                throw new IllegalStateException("sensitive failure detail: " + value);
            }
        };
    }

    private static MaskTypeDefinition identityDefinition(final String code) {
        return new MaskTypeDefinition() {
            @Override
            public String getTypeCode() {
                return code;
            }

            @Override
            public String mask(String value) {
                return value;
            }
        };
    }
}
