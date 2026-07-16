/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.integration;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.CapturedHttpEvents;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.LogMaskRestTemplateAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UntypedBodyObservationIntegrationTest {

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
    void jsonLookingStringBodiesRemainStringsWithoutChangingWireOrBusinessValues() {
        String requestBody = "{\"phone\":\"13800138000\",\"side\":\"request\"}";
        String responseBody = "{\"phone\":\"13900139000\",\"side\":\"response\"}";

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/string-body"))
                    .andExpect(content().string(requestBody))
                    .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
            RequestEntity<String> request = RequestEntity
                    .post(URI.create("https://api.example.com/string-body"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody);

            ResponseEntity<String> response = restTemplate.exchange(request, String.class);

            server.verify();
            assertEquals(responseBody, response.getBody());
        });

        JsonNode event = singleEvent();
        assertSuccessfulTextBody(event.path("request"), requestBody);
        assertSuccessfulTextBody(event.path("response"), responseBody);
    }

    @Test
    void jsonLookingByteArrayBodiesUseJacksonBase64WithoutChangingWireOrBusinessValues() {
        byte[] requestBody = utf8("{\"phone\":\"13800138000\",\"side\":\"request\"}");
        byte[] responseBody = utf8("{\"phone\":\"13900139000\",\"side\":\"response\"}");
        byte[] originalRequest = requestBody.clone();

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/byte-array-body"))
                    .andExpect(content().bytes(requestBody))
                    .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
            RequestEntity<byte[]> request = RequestEntity
                    .post(URI.create("https://api.example.com/byte-array-body"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody);

            ResponseEntity<byte[]> response = restTemplate.exchange(request, byte[].class);

            server.verify();
            assertArrayEquals(responseBody, response.getBody());
        });

        assertArrayEquals(originalRequest, requestBody);
        JsonNode event = singleEvent();
        assertSuccessfulTextBody(event.path("request"), jacksonBase64(requestBody));
        assertSuccessfulTextBody(event.path("response"), jacksonBase64(responseBody));
    }

    @Test
    void emptyByteArrayRequestIsAnEmptyStringRatherThanNull() {
        byte[] requestBody = new byte[0];
        byte[] responseBody = utf8("{\"side\":\"response\"}");

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/empty-byte-array"))
                    .andExpect(content().bytes(requestBody))
                    .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
            RequestEntity<byte[]> request = RequestEntity
                    .post(URI.create("https://api.example.com/empty-byte-array"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody);

            ResponseEntity<byte[]> response = restTemplate.exchange(request, byte[].class);

            server.verify();
            assertArrayEquals(responseBody, response.getBody());
        });

        JsonNode event = singleEvent();
        assertSuccessfulTextBody(event.path("request"), "");
        assertSuccessfulTextBody(event.path("response"), jacksonBase64(responseBody));
    }

    private void assertSuccessfulTextBody(JsonNode region, String expected) {
        assertEquals("SUCCESS", region.path("bodyState").textValue());
        assertTrue(region.path("body").isTextual());
        assertEquals(expected, region.path("body").textValue());
    }

    private String jacksonBase64(byte[] value) {
        return objectMapper.valueToTree(value).asText();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private JsonNode singleEvent() {
        assertEquals(1, events.getEvents().size());
        try {
            return objectMapper.readTree(events.getEvents().get(0).getFormattedMessage());
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

}
