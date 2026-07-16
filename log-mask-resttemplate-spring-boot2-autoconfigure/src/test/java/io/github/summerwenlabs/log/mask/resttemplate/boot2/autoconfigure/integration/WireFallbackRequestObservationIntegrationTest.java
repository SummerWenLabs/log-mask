package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

class WireFallbackRequestObservationIntegrationTest {

    private static final MediaType WIRE_MEDIA_TYPE =
            new MediaType("application", "x-observed-wire");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class))
            .withUserConfiguration(WireConverterConfiguration.class);
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
    void declaredIso88591WireBytesAreRecordedAsStrictTextWithoutFieldParsing() {
        String wireText = "label=caf\u00e9&phone=13800138000";
        byte[] wireBytes = wireText.getBytes(StandardCharsets.ISO_8859_1);
        MediaType contentType = new MediaType(
                WIRE_MEDIA_TYPE.getType(),
                WIRE_MEDIA_TYPE.getSubtype(),
                StandardCharsets.ISO_8859_1);

        assertWireFallback(
                "https://api.example.com/wire-fallback/iso-8859-1",
                contentType,
                wireBytes,
                wireText);
    }

    @Test
    void undeclaredCharsetWireBytesAreRecordedAsStrictUtf8TextWithoutFieldParsing() {
        String wireText = "label=\u96ea&phone=13800138000";
        byte[] wireBytes = wireText.getBytes(StandardCharsets.UTF_8);

        assertWireFallback(
                "https://api.example.com/wire-fallback/utf-8",
                WIRE_MEDIA_TYPE,
                wireBytes,
                wireText);
    }

    @Test
    void incompletelyDecodableWireBytesAreRecordedAsDeterministicBase64() {
        byte[] wireBytes = new byte[] {
                'o', 'k', '=', (byte) 0xe4, (byte) 0xb8
        };

        assertWireFallback(
                "https://api.example.com/wire-fallback/base64",
                WIRE_MEDIA_TYPE,
                wireBytes,
                Base64.getEncoder().encodeToString(wireBytes));
    }

    private void assertWireFallback(
            String url,
            MediaType contentType,
            byte[] wireBytes,
            String expectedLogBody) {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo(url))
                    .andExpect(content().bytes(wireBytes))
                    .andRespond(withNoContent());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<WirePayload>(new WirePayload(wireBytes), headers),
                    Void.class);

            server.verify();
            assertEquals(204, response.getStatusCodeValue());
            assertNull(response.getBody());
        });

        JsonNode event = singleEvent();
        JsonNode request = event.path("request");
        assertEquals("SUCCESS", request.path("bodyState").textValue());
        assertTrue(request.path("body").isTextual());
        assertEquals(expectedLogBody, request.path("body").textValue());
    }

    private JsonNode singleEvent() {
        assertEquals(1, events.getEvents().size());
        try {
            return objectMapper.readTree(events.getEvents().get(0).getFormattedMessage());
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    static final class WirePayload {
        private final byte[] bytes;

        WirePayload(byte[] bytes) {
            this.bytes = bytes;
        }

        byte[] getBytes() {
            return bytes;
        }
    }

    static final class WirePayloadHttpMessageConverter
            extends AbstractHttpMessageConverter<WirePayload> {

        WirePayloadHttpMessageConverter() {
            super(MediaType.ALL);
        }

        @Override
        protected boolean supports(Class<?> type) {
            return WirePayload.class == type;
        }

        @Override
        protected WirePayload readInternal(
                Class<? extends WirePayload> type,
                HttpInputMessage inputMessage) {
            throw new UnsupportedOperationException("request-only test converter");
        }

        @Override
        protected void writeInternal(
                WirePayload payload,
                HttpOutputMessage outputMessage) throws IOException {
            outputMessage.getBody().write(payload.getBytes());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class WireConverterConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate wireRestTemplate() {
            HttpMessageConverter<?> converter = new WirePayloadHttpMessageConverter();
            return new RestTemplate(
                    Collections.<HttpMessageConverter<?>>singletonList(converter));
        }
    }

}
