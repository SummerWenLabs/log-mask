package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TypedJsonBodyObservationBoundaryIntegrationTest {

    private static final int DEFAULT_MAX_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class));
    private CapturedHttpEvents events;

    @BeforeEach
    void captureEvents() {
        events = new CapturedHttpEvents();
    }

    @AfterEach
    void releaseEvents() {
        events.close();
    }

    @Test
    void oversizedTypedRequestIsOmittedWithoutChangingWireOrResponse() throws Exception {
        LargePayload request = new LargePayload("request", largeText());
        String expectedWire = objectMapper.writeValueAsString(request);
        assertTrue(expectedWire.getBytes(StandardCharsets.UTF_8).length
                > DEFAULT_MAX_BODY_BYTES);

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/oversized-request"))
                    .andExpect(content().json(expectedWire))
                    .andRespond(withSuccess(
                            "{\"label\":\"response\",\"value\":\"complete\"}",
                            MediaType.APPLICATION_JSON));

            ResponseEntity<LargePayload> response = restTemplate.exchange(
                    "https://api.example.com/oversized-request",
                    HttpMethod.POST,
                    new HttpEntity<LargePayload>(request),
                    LargePayload.class);

            server.verify();
            assertNotNull(response.getBody());
            assertEquals("complete", response.getBody().getValue());
        });

        assertEquals(largeText(), request.getValue());
        JsonNode event = singleEvent();
        assertLimitExceeded(event.path("request"));
        assertEquals("SUCCESS", event.path("response").path("bodyState").textValue());
        assertEquals("complete", event.path("response").path("body").path("value").textValue());
    }

    @Test
    void oversizedTypedResponseIsOmittedWithoutChangingRequestOrBusinessValue()
            throws Exception {
        LargePayload request = new LargePayload("request", "complete");
        LargePayload responsePayload = new LargePayload("response", largeText());
        String expectedRequestWire = objectMapper.writeValueAsString(request);
        String responseWire = objectMapper.writeValueAsString(responsePayload);
        assertTrue(responseWire.getBytes(StandardCharsets.UTF_8).length
                > DEFAULT_MAX_BODY_BYTES);

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/oversized-response"))
                    .andExpect(content().json(expectedRequestWire))
                    .andRespond(withSuccess(responseWire, MediaType.APPLICATION_JSON));

            ResponseEntity<LargePayload> response = restTemplate.exchange(
                    "https://api.example.com/oversized-response",
                    HttpMethod.POST,
                    new HttpEntity<LargePayload>(request),
                    LargePayload.class);

            server.verify();
            assertNotNull(response.getBody());
            assertEquals("response", response.getBody().getLabel());
            assertEquals(largeText(), response.getBody().getValue());
        });

        JsonNode event = singleEvent();
        assertEquals("SUCCESS", event.path("request").path("bodyState").textValue());
        assertEquals("complete", event.path("request").path("body").path("value").textValue());
        assertLimitExceeded(event.path("response"));
    }

    @Test
    void nullAbsentAndEmptyTypedBodiesKeepDistinctJsonShapes() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            exchangeAbsentBody(restTemplate, server);
            exchangeEmptyObject(restTemplate, server);
            exchangeEmptyList(restTemplate, server);
            exchangeEmptyMap(restTemplate, server);
            server.verify();
        });

        assertEquals(4, events.getEvents().size());
        assertBodyShapes(readEvent(0), null, null);
        assertBodyShapes(readEvent(1), objectMapper.createObjectNode(),
                objectMapper.createObjectNode());
        assertBodyShapes(readEvent(2), objectMapper.createArrayNode(),
                objectMapper.createArrayNode());
        assertBodyShapes(readEvent(3), objectMapper.createObjectNode(),
                objectMapper.createObjectNode());
    }

    @Test
    void disabledInfoSkipsTypedSnapshotsAndExchangeEvent() {
        events.disableInfo();
        CountingPayload request = new CountingPayload("request");

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/info-disabled"))
                    .andExpect(content().json("{\"value\":\"request\"}"))
                    .andRespond(withSuccess(
                            "{\"value\":\"response\"}",
                            MediaType.APPLICATION_JSON));

            ResponseEntity<CountingPayload> response = restTemplate.exchange(
                    "https://api.example.com/info-disabled",
                    HttpMethod.POST,
                    new HttpEntity<CountingPayload>(request),
                    CountingPayload.class);

            server.verify();
            assertNotNull(response.getBody());
            assertEquals("response", response.getBody().peekValue());
            assertEquals(0, response.getBody().getterInvocations());
        });

        assertEquals(1, request.getterInvocations());
        assertTrue(events.getEvents().isEmpty());
    }

    private void exchangeAbsentBody(
            RestTemplate restTemplate,
            MockRestServiceServer server) {
        server.expect(once(), requestTo("https://api.example.com/absent"))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        ResponseEntity<LargePayload> response = restTemplate.exchange(
                "https://api.example.com/absent",
                HttpMethod.POST,
                new HttpEntity<LargePayload>((LargePayload) null),
                LargePayload.class);

        assertNull(response.getBody());
        server.verify();
        server.reset();
    }

    private void exchangeEmptyObject(
            RestTemplate restTemplate,
            MockRestServiceServer server) {
        ObjectNode emptyObject = objectMapper.createObjectNode();
        server.expect(once(), requestTo("https://api.example.com/empty-object"))
                .andExpect(content().json("{}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ResponseEntity<ObjectNode> response = restTemplate.exchange(
                "https://api.example.com/empty-object",
                HttpMethod.POST,
                new HttpEntity<ObjectNode>(emptyObject),
                ObjectNode.class);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        server.verify();
        server.reset();
    }

    private void exchangeEmptyList(
            RestTemplate restTemplate,
            MockRestServiceServer server) {
        List<LargePayload> emptyList = Collections.emptyList();
        ParameterizedTypeReference<List<LargePayload>> type =
                new ParameterizedTypeReference<List<LargePayload>>() {
                };
        server.expect(once(), requestTo("https://api.example.com/empty-list"))
                .andExpect(content().json("[]"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        RequestEntity<List<LargePayload>> request = RequestEntity
                .post(URI.create("https://api.example.com/empty-list"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(emptyList, type.getType());

        ResponseEntity<List<LargePayload>> response = restTemplate.exchange(request, type);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        server.verify();
        server.reset();
    }

    private void exchangeEmptyMap(
            RestTemplate restTemplate,
            MockRestServiceServer server) {
        Map<String, LargePayload> emptyMap =
                Collections.<String, LargePayload>emptyMap();
        ParameterizedTypeReference<Map<String, LargePayload>> type =
                new ParameterizedTypeReference<Map<String, LargePayload>>() {
                };
        server.expect(once(), requestTo("https://api.example.com/empty-map"))
                .andExpect(content().json("{}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        RequestEntity<Map<String, LargePayload>> request = RequestEntity
                .post(URI.create("https://api.example.com/empty-map"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(emptyMap, type.getType());

        ResponseEntity<Map<String, LargePayload>> response = restTemplate.exchange(request, type);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        server.verify();
        server.reset();
    }

    private void assertBodyShapes(JsonNode event, JsonNode requestBody, JsonNode responseBody) {
        assertEquals("SUCCESS", event.path("request").path("bodyState").textValue());
        assertEquals("SUCCESS", event.path("response").path("bodyState").textValue());
        assertJsonShape(event.path("request").path("body"), requestBody);
        assertJsonShape(event.path("response").path("body"), responseBody);
    }

    private static void assertJsonShape(JsonNode actual, JsonNode expected) {
        if (expected == null) {
            assertTrue(actual.isNull());
        } else {
            assertEquals(expected, actual);
        }
    }

    private static void assertLimitExceeded(JsonNode region) {
        assertEquals("LIMIT_EXCEEDED", region.path("bodyState").textValue());
        assertTrue(region.path("body").isTextual());
        assertEquals("", region.path("body").textValue());
    }

    private JsonNode singleEvent() {
        assertEquals(1, events.getEvents().size());
        return readEvent(0);
    }

    private JsonNode readEvent(int index) {
        try {
            return objectMapper.readTree(events.getEvents().get(index).getFormattedMessage());
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    private static String largeText() {
        char[] value = new char[70 * 1024];
        Arrays.fill(value, 'x');
        return new String(value);
    }

    static final class LargePayload {
        private String label;
        private String value;

        LargePayload() {
        }

        LargePayload(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static final class CountingPayload {
        private String value;
        private int getterInvocations;

        CountingPayload() {
        }

        CountingPayload(String value) {
            this.value = value;
        }

        public String getValue() {
            getterInvocations++;
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        String peekValue() {
            return value;
        }

        int getterInvocations() {
            return getterInvocations;
        }
    }

}
