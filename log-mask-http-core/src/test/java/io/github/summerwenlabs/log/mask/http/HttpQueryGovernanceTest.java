package io.github.summerwenlabs.log.mask.http;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import io.github.summerwenlabs.log.mask.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.MaskTypeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpQueryGovernanceTest {

    @Test
    void redactsEveryRepeatedValueWithoutChangingOccurrenceOrderOrTheRequest() {
        URI requestUri = URI.create(
                "https://api.example.com/search?token=first&tag=java&token=second");
        HttpQueryGovernance governance = HttpQueryGovernance.of(
                Collections.singletonList(
                        HttpQueryRule.builder()
                                .name("token")
                                .type(HttpRuleType.REDACT)
                                .build()),
                MaskStrategyRegistry.empty());

        HttpRequestUri result = HttpRequestUri.from(requestUri, governance);

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                "https://api.example.com/search?token=%3Credacted%3E"
                        + "&tag=java&token=%3Credacted%3E",
                result.getFull());
        assertEquals(
                java.util.Arrays.asList("<redacted>", "<redacted>"),
                values(result, "token"));
        assertEquals(Collections.singletonList("java"), values(result, "tag"));
        assertEquals(
                "https://api.example.com/search?token=first&tag=java&token=second",
                requestUri.toString());
    }

    @Test
    void prefersNormalizedHostRulesAndMatchesDecodedNamesCaseSensitively() {
        HttpQueryGovernance governance = HttpQueryGovernance.of(
                Arrays.asList(
                        HttpQueryRule.builder()
                                .name("token")
                                .type(HttpRuleType.FULL)
                                .build(),
                        HttpQueryRule.builder()
                                .name("token")
                                .host("API.Example.COM")
                                .type(HttpRuleType.REDACT)
                                .build()),
                MaskStrategyRegistry.empty());

        HttpRequestUri scoped = HttpRequestUri.from(
                URI.create("https://api.example.com/search?%74oken=1234&Token=visible"),
                governance);
        HttpRequestUri global = HttpRequestUri.from(
                URI.create("https://other.example.com/search?token=1234"),
                governance);

        assertEquals(
                "https://api.example.com/search?%74oken=%3Credacted%3E&Token=visible",
                scoped.getFull());
        assertEquals(Collections.singletonList("<redacted>"), values(scoped, "token"));
        assertEquals(Collections.singletonList("visible"), values(scoped, "Token"));
        assertEquals(
                "https://other.example.com/search?token=%2A%2A%2A%2A",
                global.getFull());
        assertEquals(Collections.singletonList("****"), values(global, "token"));
    }

    @Test
    void rejectsInvalidStrategySelectorsAndDuplicateNormalizedScopes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpQueryRule.builder().name("token").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpQueryRule.builder()
                        .name("token")
                        .type(HttpRuleType.REDACT)
                        .typeCode("TOKEN")
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpQueryRule.builder().name("token").typeCode("").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpQueryRule.builder().name("token").typeCode(" TOKEN").build());

        HttpQueryRule first = HttpQueryRule.builder()
                .name("token")
                .host("API.Example.COM")
                .type(HttpRuleType.REDACT)
                .build();
        HttpQueryRule duplicate = HttpQueryRule.builder()
                .name("token")
                .host("api.example.com")
                .type(HttpRuleType.REDACT)
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> HttpQueryGovernance.of(
                        Arrays.asList(first, duplicate),
                        MaskStrategyRegistry.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpQueryGovernance.of(
                        Collections.singletonList(typeCodeRule("token", "MISSING")),
                        MaskStrategyRegistry.empty()));
    }

    @Test
    void excludesEveryMatchingOccurrenceWhileKeepingOtherOrderAndValueKinds() {
        HttpQueryGovernance governance = HttpQueryGovernance.of(
                Collections.singletonList(
                        HttpQueryRule.builder()
                                .name("secret")
                                .type(HttpRuleType.EXCLUDE)
                                .build()),
                MaskStrategyRegistry.empty());

        HttpRequestUri result = HttpRequestUri.from(
                URI.create(
                        "https://example.com/search?first=1&secret=one&flag"
                                + "&empty=&first=2&%73ecret=two"),
                governance);
        HttpRequestUri onlyExcluded = HttpRequestUri.from(
                URI.create("https://example.com/search?secret=one&secret=two"),
                governance);

        assertEquals(
                "https://example.com/search?first=1&flag&empty=&first=2",
                result.getFull());
        assertEquals(
                Arrays.asList("first", "flag", "empty"),
                names(result));
        assertEquals(Arrays.asList("1", "2"), values(result, "first"));
        assertEquals(Collections.singletonList(null), values(result, "flag"));
        assertEquals(Collections.singletonList(""), values(result, "empty"));
        assertEquals("https://example.com/search", onlyExcluded.getFull());
        assertEquals(Collections.emptyList(), onlyExcluded.getQuery().getEntries());
    }

    @Test
    void encodesCustomResultsAsOneValueAndAppliesFallbackOnlyToFailedValues() {
        MaskStrategyRegistry registry = MaskStrategyRegistry.of(Arrays.asList(
                definition("INJECT", "a&admin=true +%\u4E2D"),
                new MaskTypeDefinition() {
                    @Override
                    public String getTypeCode() {
                        return "THROW";
                    }

                    @Override
                    public String mask(String value) {
                        throw new IllegalStateException("failed");
                    }
                },
                definition("NULL", null)));
        HttpQueryGovernance governance = HttpQueryGovernance.of(
                Arrays.asList(
                        typeCodeRule("safe", "INJECT"),
                        typeCodeRule("bad", "INJECT"),
                        typeCodeRule("boom", "THROW"),
                        typeCodeRule("nullResult", "NULL")),
                registry);

        HttpRequestUri result = HttpRequestUri.from(
                URI.create(
                        "https://example.com/search?safe=value&bad=%FF&untouched=%FF"
                                + "&boom=value&nullResult=value"),
                governance);

        assertEquals(RegionState.FALLBACK_APPLIED, result.getState());
        assertEquals(
                "https://example.com/search?safe=a%26admin%3Dtrue%20%2B%25%E4%B8%AD"
                        + "&bad=%3Credacted%3E&untouched=%FF&boom=%3Credacted%3E"
                        + "&nullResult=%3Credacted%3E",
                result.getFull());
        assertEquals(
                Collections.singletonList("a&admin=true +%\u4E2D"),
                values(result, "safe"));
        assertEquals(Collections.singletonList("<redacted>"), values(result, "bad"));
        assertEquals(Collections.singletonList("%FF"), values(result, "untouched"));
        assertEquals(Collections.singletonList("<redacted>"), values(result, "boom"));
        assertEquals(
                Collections.singletonList("<redacted>"),
                values(result, "nullResult"));
    }

    @Test
    void keepsBareFlagsDistinctFromEmptyValuesWhenGoverned() {
        HttpQueryGovernance governance = HttpQueryGovernance.of(
                Arrays.asList(
                        HttpQueryRule.builder()
                                .name("redacted")
                                .type(HttpRuleType.REDACT)
                                .build(),
                        HttpQueryRule.builder()
                                .name("full")
                                .type(HttpRuleType.FULL)
                                .build()),
                MaskStrategyRegistry.empty());

        HttpRequestUri result = HttpRequestUri.from(
                URI.create(
                        "https://example.com/search?redacted&redacted=&full&full="),
                governance);

        assertEquals(
                "https://example.com/search?redacted&redacted=%3Credacted%3E&full&full=",
                result.getFull());
        assertEquals(Arrays.asList(null, "<redacted>"), values(result, "redacted"));
        assertEquals(Arrays.asList(null, ""), values(result, "full"));
    }

    @Test
    void containsUndecodableComponentsAndKeepsProcessingOtherParameters() {
        HttpQueryGovernance governance = HttpQueryGovernance.of(
                Arrays.asList(
                        HttpQueryRule.builder()
                                .name("%FF")
                                .type(HttpRuleType.REDACT)
                                .build(),
                        HttpQueryRule.builder()
                                .name("masked")
                                .type(HttpRuleType.FULL)
                                .build(),
                        HttpQueryRule.builder()
                                .name("bad")
                                .type(HttpRuleType.FULL)
                                .build()),
                MaskStrategyRegistry.empty());

        HttpRequestUri result = HttpRequestUri.from(
                URI.create(
                        "https://example.com/search?%FF=visible&masked=12"
                                + "&bad=%FF&plain=%FF"),
                governance);
        HttpRequestUri invalidUri = HttpRequestUri.from(
                URI.create("http://foo_bar/path?bad=%FF&masked=12"),
                governance);

        assertEquals(RegionState.FALLBACK_APPLIED, result.getState());
        assertEquals(
                "https://example.com/search?%FF=visible&masked=%2A%2A"
                        + "&bad=%3Credacted%3E&plain=%FF",
                result.getFull());
        assertEquals(Collections.singletonList("visible"), values(result, "%FF"));
        assertEquals(Collections.singletonList("**"), values(result, "masked"));
        assertEquals(Collections.singletonList("<redacted>"), values(result, "bad"));
        assertEquals(Collections.singletonList("%FF"), values(result, "plain"));
        assertEquals(RegionState.PROCESSING_FAILED, invalidUri.getState());
        assertEquals(
                "http://foo_bar/path?bad=%3Credacted%3E&masked=%2A%2A",
                invalidUri.getFull());
    }

    private static HttpQueryRule typeCodeRule(String name, String typeCode) {
        return HttpQueryRule.builder()
                .name(name)
                .typeCode(typeCode)
                .build();
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

    private static java.util.List<String> names(HttpRequestUri uri) {
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (NameValueEntry entry : uri.getQuery().getEntries()) {
            names.add(entry.getName());
        }
        return names;
    }

    private static java.util.List<String> values(HttpRequestUri uri, String name) {
        for (NameValueEntry entry : uri.getQuery().getEntries()) {
            if (name.equals(entry.getName())) {
                return entry.getValues();
            }
        }
        throw new AssertionError("Missing query entry: " + name);
    }
}
