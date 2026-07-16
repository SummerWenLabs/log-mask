package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.CapturedHttpEvents;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.LogMaskRestTemplateAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseBodyFinalizationIntegrationTest {

    private static final int DEFAULT_MAX_BODY_BYTES = 64 * 1024;
    private static final String SENSITIVE_VALUE = "sensitive-raw-9f43";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class))
            .withUserConfiguration(ControlledResponseConfiguration.class);
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
    void failedJacksonReadDoesNotFallBackToSensitiveWireBytes() {
        byte[] responseBody = utf8(
                "{\"secret\":\"" + SENSITIVE_VALUE
                        + "\",\"quantity\":\"not-an-integer\"}");

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);
            transport.prepare(responseBody, MediaType.APPLICATION_JSON);

            assertThrows(
                    RestClientException.class,
                    () -> restTemplate.getForObject(
                            "https://api.example.com/jackson-read-failure",
                            SensitivePayload.class));
        });

        String formattedMessage = singleFormattedMessage();
        assertFalse(formattedMessage.contains(SENSITIVE_VALUE));
        JsonNode event = readEvent(formattedMessage);
        JsonNode response = event.path("response");
        assertEquals(200, response.path("status").intValue());
        assertEquals("PROCESSING_FAILED", response.path("bodyState").textValue());
        assertTrue(response.path("body").isTextual());
        assertEquals("", response.path("body").textValue());
    }

    @Test
    void untypedResponseWireUsesItsDeclaredUtf16LeCharset() {
        String expectedText = "wire-response-\u20ac";
        byte[] responseBody = expectedText.getBytes(StandardCharsets.UTF_16LE);
        MediaType contentType = new MediaType(
                MediaType.TEXT_PLAIN.getType(),
                MediaType.TEXT_PLAIN.getSubtype(),
                StandardCharsets.UTF_16LE);

        byte[] actual = executeUntyped(responseBody, contentType, "/utf16le-wire");

        assertArrayEquals(responseBody, actual);
        assertSuccessfulTextBody(singleEvent().path("response"), expectedText);
    }

    @Test
    void untypedResponseWireFallsBackToBase64WhenUtf8IsInvalid() {
        byte[] responseBody = new byte[] {(byte) 0xc3, 0x28};
        MediaType contentType = new MediaType(
                MediaType.TEXT_PLAIN.getType(),
                MediaType.TEXT_PLAIN.getSubtype(),
                StandardCharsets.UTF_8);

        byte[] actual = executeUntyped(responseBody, contentType, "/invalid-utf8-wire");

        assertArrayEquals(responseBody, actual);
        assertSuccessfulTextBody(singleEvent().path("response"), "wyg=");
    }

    @Test
    void smallTypedResponseViewWinsWhenConsumedWireExceedsTheBodyBudget() {
        String padding = repeated('x', DEFAULT_MAX_BODY_BYTES + 1024);
        byte[] responseBody = utf8(
                "{\"label\":\"small-view\",\"wirePadding\":\""
                        + padding
                        + "\"}");
        assertTrue(responseBody.length > DEFAULT_MAX_BODY_BYTES);

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);
            transport.prepare(responseBody, MediaType.APPLICATION_JSON);

            SmallResponseView response = restTemplate.getForObject(
                    "https://api.example.com/small-typed-view",
                    SmallResponseView.class);

            assertNotNull(response);
            assertEquals("small-view", response.getLabel());
        });

        JsonNode response = singleEvent().path("response");
        assertEquals("SUCCESS", response.path("bodyState").textValue());
        assertEquals("small-view", response.path("body").path("label").textValue());
        assertFalse(response.path("body").has("wirePadding"));
    }

    private byte[] executeUntyped(
            byte[] responseBody,
            MediaType contentType,
            String path) {
        final byte[][] actual = new byte[1][];
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);
            transport.prepare(responseBody, contentType);
            ResponseExtractor<byte[]> readWire = response -> readFully(response.getBody());

            actual[0] = restTemplate.execute(
                    "https://api.example.com" + path,
                    HttpMethod.GET,
                    null,
                    readWire);
        });
        return actual[0];
    }

    private void assertSuccessfulTextBody(JsonNode region, String expected) {
        assertEquals("SUCCESS", region.path("bodyState").textValue());
        assertTrue(region.path("body").isTextual());
        assertEquals(expected, region.path("body").textValue());
    }

    private JsonNode singleEvent() {
        return readEvent(singleFormattedMessage());
    }

    private String singleFormattedMessage() {
        assertEquals(1, events.getEvents().size());
        return events.getEvents().get(0).getFormattedMessage();
    }

    private JsonNode readEvent(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    private static byte[] readFully(java.io.InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            result.write(buffer, 0, read);
        }
        return result.toByteArray();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String repeated(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    static final class SensitivePayload {
        private String secret;
        private int quantity;

        SensitivePayload() {
        }

        @Mask(type = MaskType.FULL)
        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class SmallResponseView {
        private String label;

        SmallResponseView() {
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ControlledResponseConfiguration {

        @Bean
        ControlledResponseFactory controlledResponseFactory() {
            return new ControlledResponseFactory();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate controlledResponseRestTemplate(
                ControlledResponseFactory requestFactory) {
            return new RestTemplate(requestFactory);
        }
    }

    static final class ControlledResponseFactory implements ClientHttpRequestFactory {
        private byte[] nextBody;
        private MediaType nextContentType;

        void prepare(byte[] body, MediaType contentType) {
            nextBody = body.clone();
            nextContentType = contentType;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            return new MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected ClientHttpResponse executeInternal() {
                    if (nextBody == null) {
                        throw new IllegalStateException("response was not prepared");
                    }
                    MockClientHttpResponse response =
                            new MockClientHttpResponse(nextBody, HttpStatus.OK);
                    response.getHeaders().setContentType(nextContentType);
                    response.getHeaders().setContentLength(nextBody.length);
                    return response;
                }
            };
        }
    }

}
