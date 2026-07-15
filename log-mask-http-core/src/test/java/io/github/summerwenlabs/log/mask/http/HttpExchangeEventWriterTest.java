package io.github.summerwenlabs.log.mask.http;

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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpExchangeEventWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpExchangeEventWriter writer = new HttpExchangeEventWriter();

    @Test
    void writesTheCanonicalNameValueModelInStandardAndCompactShapes() throws Exception {
        NameValueCollection headers = NameValueCollection.builder()
                .add("x-tag", "one")
                .add("x-empty", "")
                .add("x-tag", "two")
                .build();
        HttpExchangeRequest request = HttpExchangeRequest.builder()
                .method("GET")
                .uri(HttpRequestUri.from(URI.create(
                        "https://api.example.com/items?a=1&b=2&a=3&flag")))
                .headers(RegionState.SUCCESS, headers)
                .body(RegionState.SUCCESS, JsonValue.nullValue())
                .build();
        HttpExchangeEvent event = HttpExchangeEvent.builder()
                .timestamp(Instant.EPOCH)
                .exchangeId(UUID.fromString("4e19966c-d3cb-4fc2-b193-41d27c2fce80"))
                .durationMs(0)
                .governanceEnabled(true)
                .request(request)
                .build();

        JsonNode standard = objectMapper.readTree(writer.write(event));
        JsonNode compact = objectMapper.readTree(
                new HttpExchangeEventWriter(NameValueShape.COMPACT, true).write(event));
        JsonNode standardQuery = standard.path("request").path("uri").path("query");
        JsonNode standardHeaders = standard.path("request").path("headers");
        JsonNode compactQuery = compact.path("request").path("uri").path("query");
        JsonNode compactHeaders = compact.path("request").path("headers");

        assertEquals(
                Arrays.asList("a", "b", "flag"),
                Arrays.asList(
                        standardQuery.get(0).path("name").textValue(),
                        standardQuery.get(1).path("name").textValue(),
                        standardQuery.get(2).path("name").textValue()));
        assertEquals(
                Arrays.asList("1", "3"),
                objectMapper.convertValue(
                        standardQuery.get(0).path("values"),
                        List.class));
        assertTrue(standardQuery.get(2).path("values").get(0).isNull());
        assertEquals("x-tag", standardHeaders.get(0).path("name").textValue());
        assertEquals(
                Arrays.asList("a", "b", "flag"),
                fieldNames(compactQuery));
        assertEquals(
                Arrays.asList("1", "3"),
                objectMapper.convertValue(
                        compactQuery.path("a"),
                        List.class));
        assertEquals(
                Arrays.asList("x-tag", "x-empty"),
                fieldNames(compactHeaders));
    }

    @Test
    void writesEmptyShapesAndCanOmitOnlyUriDetails() throws Exception {
        HttpExchangeRequest request = HttpExchangeRequest.builder()
                .method("GET")
                .uri(HttpRequestUri.from(URI.create("https://api.example.com/?")))
                .headers(RegionState.SUCCESS, emptyNameValues())
                .body(RegionState.SUCCESS, JsonValue.nullValue())
                .build();
        HttpExchangeEvent event = HttpExchangeEvent.builder()
                .timestamp(Instant.EPOCH)
                .exchangeId(UUID.fromString("5ca8766a-56f0-4105-b4e0-85e9a17bed53"))
                .durationMs(0)
                .governanceEnabled(true)
                .request(request)
                .build();

        JsonNode standard = objectMapper.readTree(writer.write(event));
        JsonNode compactWithoutDetails = objectMapper.readTree(
                new HttpExchangeEventWriter(NameValueShape.COMPACT, false).write(event));
        JsonNode standardRequest = standard.path("request");
        JsonNode compactRequest = compactWithoutDetails.path("request");
        JsonNode compactUri = compactRequest.path("uri");

        assertTrue(standardRequest.path("uri").path("query").isArray());
        assertEquals(0, standardRequest.path("uri").path("query").size());
        assertTrue(standardRequest.path("headers").isArray());
        assertEquals(0, standardRequest.path("headers").size());
        assertEquals(
                Collections.singletonList("full"),
                fieldNames(compactUri));
        assertEquals(
                "https://api.example.com/?",
                compactUri.path("full").textValue());
        assertTrue(compactRequest.path("headers").isObject());
        assertEquals(0, compactRequest.path("headers").size());
    }

    @Test
    void writesACompleteSchemaVersionOneEventAsCompactJson() throws Exception {
        HttpExchangeRequest request = HttpExchangeRequest.builder()
                .method("POST")
                .uri(HttpRequestUri.from(URI.create("https://api.example.com/users")))
                .headers(RegionState.SUCCESS, emptyNameValues())
                .body(RegionState.SUCCESS, JsonValue.ofJson("{\"name\":\"Ada\"}"))
                .build();
        HttpExchangeResponse response = HttpExchangeResponse.builder()
                .status(200)
                .headers(RegionState.SUCCESS, emptyNameValues())
                .body(RegionState.SUCCESS, JsonValue.ofJson("{\"id\":1001}"))
                .build();
        HttpExchangeEvent event = HttpExchangeEvent.builder()
                .timestamp(Instant.parse("2026-07-14T10:20:30.123456Z"))
                .exchangeId(UUID.fromString("2d370e01-b47d-4d19-a301-c1f7e76bd042"))
                .traceId("4f912e51d62a4bc9")
                .durationMs(87)
                .governanceEnabled(true)
                .request(request)
                .response(response)
                .build();

        String json = writer.write(event);
        JsonNode result = objectMapper.readTree(json);

        assertFalse(json.contains("\n"));
        assertTrue(json.startsWith("{") && json.endsWith("}"));
        assertEquals(
                Arrays.asList("event", "schemaVersion", "timestamp", "exchangeId", "traceId",
                        "durationMs", "governanceEnabled", "request", "response"),
                fieldNames(result));
        assertEquals("http_exchange", result.path("event").textValue());
        assertEquals(1, result.path("schemaVersion").intValue());
        assertEquals("2026-07-14T10:20:30.123Z", result.path("timestamp").textValue());
        assertEquals("2d370e01-b47d-4d19-a301-c1f7e76bd042", result.path("exchangeId").textValue());
        assertEquals("4f912e51d62a4bc9", result.path("traceId").textValue());
        assertEquals(87L, result.path("durationMs").longValue());
        assertTrue(result.path("governanceEnabled").booleanValue());

        JsonNode requestResult = result.path("request");
        assertEquals(
                Arrays.asList("method", "uriState", "uri", "headersState", "headers", "bodyState", "body"),
                fieldNames(requestResult));
        assertEquals("POST", requestResult.path("method").textValue());
        assertEquals("SUCCESS", requestResult.path("uriState").textValue());
        assertEquals("https://api.example.com/users", requestResult.path("uri").path("full").textValue());
        assertEquals("Ada", requestResult.path("body").path("name").textValue());

        JsonNode responseResult = result.path("response");
        assertEquals(
                Arrays.asList("status", "headersState", "headers", "bodyState", "body"),
                fieldNames(responseResult));
        assertEquals(200, responseResult.path("status").intValue());
        assertEquals(1001, responseResult.path("body").path("id").intValue());
    }

    @Test
    void writesARequestOnlyEventWithExplicitNulls() throws Exception {
        HttpExchangeRequest request = HttpExchangeRequest.builder()
                .method("GET")
                .uri(HttpRequestUri.from(URI.create("https://api.example.com/health")))
                .headers(RegionState.DISABLED, null)
                .body(RegionState.SUCCESS, JsonValue.ofJson("null"))
                .build();
        HttpExchangeEvent event = HttpExchangeEvent.builder()
                .timestamp(Instant.parse("2026-07-14T10:20:30Z"))
                .exchangeId(UUID.fromString("3ee986b0-9b04-4a2f-ac9d-ff835bc45e13"))
                .durationMs(0)
                .governanceEnabled(false)
                .request(request)
                .build();

        JsonNode result = objectMapper.readTree(writer.write(event));

        assertTrue(result.path("traceId").isNull());
        assertFalse(result.path("governanceEnabled").booleanValue());
        assertEquals("DISABLED", result.path("request").path("headersState").textValue());
        assertTrue(result.path("request").path("headers").isNull());
        assertEquals("SUCCESS", result.path("request").path("bodyState").textValue());
        assertTrue(result.path("request").path("body").isNull());
        assertTrue(result.path("response").isNull());
    }

    @Test
    void writesEveryPublishedBodyRegionStateWithoutInference() throws Exception {
        for (RegionState state : RegionState.values()) {
            JsonValue bodyValue = requiresEmptyBody(state)
                    ? JsonValue.emptyString()
                    : JsonValue.ofJson("\"value\"");
            HttpExchangeRequest request = HttpExchangeRequest.builder()
                    .method("POST")
                    .uri(HttpRequestUri.from(URI.create("https://api.example.com")))
                    .headers(RegionState.SUCCESS, emptyNameValues())
                    .body(state, bodyValue)
                    .build();
            HttpExchangeEvent event = HttpExchangeEvent.builder()
                    .timestamp(Instant.EPOCH)
                    .exchangeId(UUID.fromString("7decc727-4a36-4106-a57a-90f6c55ad19f"))
                    .durationMs(1)
                    .governanceEnabled(true)
                    .request(request)
                    .build();

            JsonNode result = objectMapper.readTree(writer.write(event));

            assertEquals(state.name(), result.path("request").path("bodyState").textValue());
        }
    }

    @Test
    void rejectsLimitExceededOutsideBodyRegions() {
        NameValueCollection value = emptyNameValues();

        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeRequest.builder().headers(RegionState.LIMIT_EXCEEDED, value));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeResponse.builder().headers(RegionState.LIMIT_EXCEEDED, value));
    }

    @Test
    void rejectsDisclosedValuesForBodyStatesThatRequireAnEmptyString() {
        JsonValue disclosed = JsonValue.ofJson("{\"secret\":\"value\"}");

        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeRequest.builder().body(RegionState.LIMIT_EXCEEDED, disclosed));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeRequest.builder().body(RegionState.PROCESSING_FAILED, disclosed));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeResponse.builder().body(RegionState.DISABLED, disclosed));
        NameValueCollection disclosedHeaders = NameValueCollection.builder()
                .add("secret", "value")
                .build();
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeRequest.builder().headers(
                        RegionState.DISABLED,
                        disclosedHeaders));
    }

    @Test
    void compactsValidatedNestedJsonValues() {
        HttpExchangeRequest request = HttpExchangeRequest.builder()
                .method("GET")
                .uri(HttpRequestUri.from(URI.create("https://api.example.com")))
                .headers(RegionState.SUCCESS, emptyNameValues())
                .body(RegionState.SUCCESS, JsonValue.ofJson("null"))
                .build();
        HttpExchangeEvent event = HttpExchangeEvent.builder()
                .timestamp(Instant.EPOCH)
                .exchangeId(UUID.fromString("5ec4d2fc-7b9c-442f-9c57-b098d9cbf6ab"))
                .durationMs(0)
                .governanceEnabled(true)
                .request(request)
                .build();

        String json = writer.write(event);

        assertFalse(json.contains("\n"));
        assertTrue(json.contains(
                "\"uri\":{\"full\":\"https://api.example.com\",\"scheme\":\"https\""));
        assertTrue(json.contains("\"headers\":[]"));
    }

    @Test
    void rejectsInvalidOrMultipleJsonValues() {
        assertThrows(IllegalArgumentException.class, () -> JsonValue.ofJson(""));
        assertThrows(IllegalArgumentException.class, () -> JsonValue.ofJson("{\"broken\":"));
        assertThrows(IllegalArgumentException.class, () -> JsonValue.ofJson("null true"));
    }

    @Test
    void rejectsNegativeDurationAndNonVersionFourExchangeIds() {
        HttpExchangeRequest request = HttpExchangeRequest.builder()
                .method("GET")
                .uri(HttpRequestUri.from(URI.create("https://api.example.com")))
                .headers(RegionState.SUCCESS, emptyNameValues())
                .body(RegionState.SUCCESS, JsonValue.ofJson("null"))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeEvent.builder()
                        .timestamp(Instant.EPOCH)
                        .exchangeId(UUID.fromString("16494725-7b2c-4a4c-a59f-a6ef10808165"))
                        .durationMs(-1)
                        .governanceEnabled(true)
                        .request(request)
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpExchangeEvent.builder()
                        .timestamp(Instant.EPOCH)
                        .exchangeId(UUID.fromString("16494725-7b2c-1a4c-a59f-a6ef10808165"))
                        .durationMs(0)
                        .governanceEnabled(true)
                        .request(request)
                        .build());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<String>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            names.add(fields.next());
        }
        return names;
    }

    private static NameValueCollection emptyNameValues() {
        return NameValueCollection.builder().build();
    }

    private static boolean requiresEmptyBody(RegionState state) {
        return state == RegionState.LIMIT_EXCEEDED
                || state == RegionState.PROCESSING_FAILED
                || state == RegionState.DISABLED;
    }
}
