/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.governance;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.http.NameValueCollection;
import io.github.summerwenlabs.log.mask.http.RegionState;
import io.github.summerwenlabs.log.mask.http.exchange.HttpExchangeEvent;
import io.github.summerwenlabs.log.mask.http.exchange.HttpExchangeEventWriter;
import io.github.summerwenlabs.log.mask.http.exchange.HttpExchangeRequest;
import io.github.summerwenlabs.log.mask.http.exchange.JsonValue;
import io.github.summerwenlabs.log.mask.http.exchange.NameValueShape;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpHeaderGovernanceTest {

    @Test
    void unconfiguredHeadersStayDisclosedAndOnlyTheLogNamesAreNormalized() {
        NameValueCollection source = NameValueCollection.builder()
                .add("Authorization", "Bearer visible")
                .add("Set-Cookie", "session=one, preference=two")
                .add("Set-Cookie", "theme=dark")
                .build();
        HttpHeaderGovernance governance = HttpHeaderGovernance.of(
                Collections.<HttpHeaderRule>emptyList(),
                MaskStrategyRegistry.empty());

        HttpHeaderGovernance.Result result = governance.govern("API.EXAMPLE.COM", source);

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                Arrays.asList("authorization", "set-cookie"),
                Arrays.asList(
                        result.getHeaders().getEntries().get(0).getName(),
                        result.getHeaders().getEntries().get(1).getName()));
        assertEquals(
                Collections.singletonList("Bearer visible"),
                result.getHeaders().getEntries().get(0).getValues());
        assertEquals(
                Arrays.asList("session=one, preference=two", "theme=dark"),
                result.getHeaders().getEntries().get(1).getValues());
        assertEquals("Authorization", source.getEntries().get(0).getName());
        assertEquals("Set-Cookie", source.getEntries().get(1).getName());
    }

    @Test
    void requestAndResponseRuleSetsStayIndependent() {
        HttpHeaderGovernance requestGovernance = HttpHeaderGovernance.of(
                Collections.singletonList(
                        HttpHeaderRule.builder()
                                .name("AUTHORIZATION")
                                .type(HttpRuleType.REDACT)
                                .build()),
                MaskStrategyRegistry.empty());
        HttpHeaderGovernance responseGovernance = HttpHeaderGovernance.of(
                Collections.<HttpHeaderRule>emptyList(),
                MaskStrategyRegistry.empty());
        NameValueCollection requestHeaders = NameValueCollection.builder()
                .add("Authorization", "Bearer request-secret")
                .build();
        NameValueCollection responseHeaders = NameValueCollection.builder()
                .add("Authorization", "Bearer response-visible")
                .build();

        HttpHeaderGovernance.Result request = requestGovernance.govern(
                "api.example.com", requestHeaders);
        HttpHeaderGovernance.Result response = responseGovernance.govern(
                "api.example.com", responseHeaders);

        assertEquals(RegionState.SUCCESS, request.getState());
        assertEquals(
                Collections.singletonList("<redacted>"),
                request.getHeaders().getEntries().get(0).getValues());
        assertEquals(RegionState.SUCCESS, response.getState());
        assertEquals(
                Collections.singletonList("Bearer response-visible"),
                response.getHeaders().getEntries().get(0).getValues());
    }

    @Test
    void aConfiguredHeaderKeepsItsEmptyValueList() {
        HttpHeaderGovernance governance = HttpHeaderGovernance.of(
                Collections.singletonList(
                        HttpHeaderRule.builder()
                                .name("X-Empty")
                                .type(HttpRuleType.REDACT)
                                .build()),
                MaskStrategyRegistry.empty());
        NameValueCollection source = NameValueCollection.builder()
                .addAll("X-Empty", Collections.<String>emptyList())
                .build();

        HttpHeaderGovernance.Result result = governance.govern("api.example.com", source);

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(1, result.getHeaders().getEntries().size());
        assertEquals("x-empty", result.getHeaders().getEntries().get(0).getName());
        assertEquals(
                Collections.emptyList(),
                result.getHeaders().getEntries().get(0).getValues());
    }

    @Test
    void aHostRuleOverridesTheGlobalRuleAndContentMaskingKeepsNullValues() {
        HttpHeaderGovernance governance = HttpHeaderGovernance.of(
                Arrays.asList(
                        HttpHeaderRule.builder()
                                .name("X-Customer")
                                .type(HttpRuleType.REDACT)
                                .build(),
                        HttpHeaderRule.builder()
                                .name("x-customer")
                                .host("API.EXAMPLE.COM")
                                .type(HttpRuleType.PHONE)
                                .build()),
                MaskStrategyRegistry.empty());
        NameValueCollection source = NameValueCollection.builder()
                .add("X-Customer", null)
                .add("X-Customer", "13800138000")
                .build();

        HttpHeaderGovernance.Result scoped = governance.govern("api.example.com", source);
        HttpHeaderGovernance.Result global = governance.govern("other.example.com", source);

        assertEquals(RegionState.SUCCESS, scoped.getState());
        assertEquals(
                Arrays.asList(null, "138****8000"),
                scoped.getHeaders().getEntries().get(0).getValues());
        assertEquals(
                Arrays.asList("<redacted>", "<redacted>"),
                global.getHeaders().getEntries().get(0).getValues());
    }

    @Test
    void aCustomStrategyFailureRedactsOnlyThatValueAndMarksTheRegionFallback() {
        MaskTypeDefinition custom = new MaskTypeDefinition() {
            @Override
            public String getTypeCode() {
                return "dependency-aware";
            }

            @Override
            public String mask(String value) {
                if ("fail".equals(value)) {
                    throw new IllegalStateException("sensitive failure details");
                }
                return "masked-" + value;
            }
        };
        HttpHeaderGovernance governance = HttpHeaderGovernance.of(
                Collections.singletonList(
                        HttpHeaderRule.builder()
                                .name("X-Custom")
                                .typeCode("dependency-aware")
                                .build()),
                MaskStrategyRegistry.of(Collections.singletonList(custom)));
        NameValueCollection source = NameValueCollection.builder()
                .add("X-Custom", "first")
                .add("X-Custom", "fail")
                .add("X-Custom", "last")
                .build();

        HttpHeaderGovernance.Result result = governance.govern("api.example.com", source);

        assertEquals(RegionState.FALLBACK_APPLIED, result.getState());
        assertEquals(
                Arrays.asList("masked-first", "<redacted>", "masked-last"),
                result.getHeaders().getEntries().get(0).getValues());
        assertEquals(
                Arrays.asList("first", "fail", "last"),
                source.getEntries().get(0).getValues());
    }

    @Test
    void excludeRemovesTheWholeEntryAndCaseVariantsMergeAtFirstOccurrence() {
        HttpHeaderGovernance governance = HttpHeaderGovernance.of(
                Collections.singletonList(
                        HttpHeaderRule.builder()
                                .name("X-Drop")
                                .type(HttpRuleType.EXCLUDE)
                                .build()),
                MaskStrategyRegistry.empty());
        NameValueCollection source = NameValueCollection.builder()
                .add("X-First", "one")
                .add("X-Drop", "secret-one")
                .add("x-first", "two")
                .add("X-Drop", "secret-two")
                .add("X-Last", "three")
                .build();

        HttpHeaderGovernance.Result result = governance.govern("api.example.com", source);

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                Arrays.asList("x-first", "x-last"),
                Arrays.asList(
                        result.getHeaders().getEntries().get(0).getName(),
                        result.getHeaders().getEntries().get(1).getName()));
        assertEquals(
                Arrays.asList("one", "two"),
                result.getHeaders().getEntries().get(0).getValues());
        assertEquals(
                Collections.singletonList("three"),
                result.getHeaders().getEntries().get(1).getValues());
    }

    @Test
    void rulesRequireOneStrategyAndRejectDuplicateNormalizedScopes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpHeaderRule.builder().name("X-Secret").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpHeaderRule.builder()
                        .name("X-Secret")
                        .type(HttpRuleType.REDACT)
                        .typeCode("custom")
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpHeaderRule.builder()
                        .name("")
                        .type(HttpRuleType.REDACT)
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpHeaderRule.builder()
                        .name("X-Secret")
                        .typeCode(" ")
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpHeaderRule.builder()
                        .name("X-Secret")
                        .typeCode(" custom")
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpHeaderGovernance.of(
                        Arrays.asList(
                                HttpHeaderRule.builder()
                                        .name("X-Secret")
                                        .host("API.EXAMPLE.COM")
                                        .type(HttpRuleType.REDACT)
                                        .build(),
                                HttpHeaderRule.builder()
                                        .name("x-secret")
                                        .host("api.example.com")
                                        .type(HttpRuleType.REDACT)
                                        .build()),
                        MaskStrategyRegistry.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpHeaderGovernance.of(
                        Collections.singletonList(
                                HttpHeaderRule.builder()
                                        .name("X-Secret")
                                        .typeCode("missing")
                                        .build()),
                        MaskStrategyRegistry.empty()));
    }

    @Test
    void standardAndCompactOutputKeepTheSameHeaderOrderAndValues() throws Exception {
        NameValueCollection source = NameValueCollection.builder()
                .add("X-Tag", "one")
                .add("X-Empty", "")
                .add("X-Tag", "two")
                .build();
        HttpHeaderGovernance.Result governed = HttpHeaderGovernance.none().govern(
                "api.example.com", source);
        HttpExchangeRequest request = HttpExchangeRequest.builder()
                .method("GET")
                .uri(HttpRequestUri.from(URI.create("https://api.example.com/health")))
                .headers(governed.getState(), governed.getHeaders())
                .body(RegionState.SUCCESS, JsonValue.nullValue())
                .build();
        HttpExchangeEvent event = HttpExchangeEvent.builder()
                .timestamp(Instant.EPOCH)
                .exchangeId(UUID.fromString("27e2d651-6ea3-4dc6-8c44-42c249843a49"))
                .durationMs(1)
                .governanceEnabled(true)
                .request(request)
                .build();
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode standard = objectMapper.readTree(
                new HttpExchangeEventWriter(NameValueShape.STANDARD, true).write(event))
                .path("request").path("headers");
        JsonNode compact = objectMapper.readTree(
                new HttpExchangeEventWriter(NameValueShape.COMPACT, true).write(event))
                .path("request").path("headers");

        assertEquals("x-tag", standard.get(0).path("name").textValue());
        assertEquals(
                Arrays.asList("one", "two"),
                textValues(standard.get(0).path("values")));
        assertEquals("x-empty", standard.get(1).path("name").textValue());
        assertEquals(Arrays.asList("x-tag", "x-empty"), fieldNames(compact));
        assertEquals(Collections.singletonList(""), textValues(compact.path("x-empty")));
        assertEquals(Arrays.asList("one", "two"), textValues(compact.path("x-tag")));
    }

    @Test
    void headerValuesAreNotSubjectToTheBodySizeBudget() {
        char[] content = new char[70 * 1024];
        Arrays.fill(content, 'x');
        String value = new String(content);
        NameValueCollection source = NameValueCollection.builder()
                .add("X-Large", value)
                .build();

        HttpHeaderGovernance.Result result = HttpHeaderGovernance.none().govern(
                "api.example.com", source);

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                value,
                result.getHeaders().getEntries().get(0).getValues().get(0));
    }

    private static List<String> fieldNames(JsonNode object) {
        List<String> result = new ArrayList<String>();
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            result.add(fields.next());
        }
        return result;
    }

    private static List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<String>();
        for (JsonNode value : values) {
            result.add(value.isNull() ? null : value.textValue());
        }
        return result;
    }
}
