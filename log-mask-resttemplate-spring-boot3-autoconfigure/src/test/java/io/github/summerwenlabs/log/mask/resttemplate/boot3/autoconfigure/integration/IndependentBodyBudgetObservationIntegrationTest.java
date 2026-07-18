/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.integration;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.CapturedHttpEvents;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.LogMaskRestTemplateAutoConfiguration;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBodyBudgetObservationIntegrationTest {

    private static final int DEFAULT_MAX_BODY_BYTES = 64 * 1024;
    private static final MediaType UTF_16LE_TEXT =
            new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_16LE);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class))
            .withUserConfiguration(BudgetTransportConfiguration.class);
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
    void utf16WireFallbackCanExceedTheJsonBudgetInRawBytes() throws IOException {
        String responseBody = repeated(
                DEFAULT_MAX_BODY_BYTES / 2 + 1024,
                'r');
        byte[] wireBody = responseBody.getBytes(StandardCharsets.UTF_16LE);
        byte[] jsonBody = objectMapper.writeValueAsBytes(responseBody);

        assertTrue(wireBody.length > DEFAULT_MAX_BODY_BYTES);
        assertTrue(jsonBody.length < DEFAULT_MAX_BODY_BYTES);

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            BudgetResponseFactory transport =
                    context.getBean(BudgetResponseFactory.class);
            WireTextHttpMessageConverter converter =
                    context.getBean(WireTextHttpMessageConverter.class);
            transport.prepare(wireBody, UTF_16LE_TEXT);

            ResponseEntity<WireText> response = restTemplate.getForEntity(
                    "https://api.example.com/utf16-wire-budget",
                    WireText.class);

            assertNotNull(response.getBody());
            assertEquals(responseBody, response.getBody().getValue());
            assertEquals(1, converter.getReadCount());
        });

        JsonNode response = singleEvent().path("response");
        assertSuccessfulTextBody(response, responseBody);
    }

    @Test
    void oversizedRequestDoesNotConsumeTheResponseBudget() {
        byte[] requestBody = repeatedBytes(
                DEFAULT_MAX_BODY_BYTES + 1024,
                (byte) 'q');
        String responseBody = "small-response";

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            BudgetResponseFactory transport =
                    context.getBean(BudgetResponseFactory.class);
            transport.prepare(
                    responseBody.getBytes(StandardCharsets.UTF_8),
                    MediaType.TEXT_PLAIN);
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.example.com/independent-body-budgets",
                    HttpMethod.POST,
                    new HttpEntity<byte[]>(requestBody, requestHeaders),
                    String.class);

            assertEquals(responseBody, response.getBody());
            assertArrayEquals(requestBody, transport.getLastRequestBody());
        });

        JsonNode event = singleEvent();
        JsonNode request = event.path("request");
        assertEquals("LIMIT_EXCEEDED", request.path("bodyState").textValue());
        assertTrue(request.path("body").isTextual());
        assertEquals("", request.path("body").textValue());
        assertSuccessfulTextBody(event.path("response"), responseBody);
    }

    private void assertSuccessfulTextBody(JsonNode region, String expected) {
        assertEquals("SUCCESS", region.path("bodyState").textValue());
        assertTrue(region.path("body").isTextual());
        assertEquals(expected, region.path("body").textValue());
    }

    private JsonNode singleEvent() {
        assertEquals(1, events.getEvents().size());
        try {
            return objectMapper.readTree(
                    events.getEvents().get(0).getFormattedMessage());
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    private static String repeated(int size, char value) {
        char[] result = new char[size];
        Arrays.fill(result, value);
        return new String(result);
    }

    private static byte[] repeatedBytes(int size, byte value) {
        byte[] result = new byte[size];
        Arrays.fill(result, value);
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    static class BudgetTransportConfiguration {

        @Bean
        BudgetResponseFactory budgetResponseFactory() {
            return new BudgetResponseFactory();
        }

        @Bean
        WireTextHttpMessageConverter wireTextHttpMessageConverter() {
            return new WireTextHttpMessageConverter();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate budgetRestTemplate(
                BudgetResponseFactory requestFactory,
                WireTextHttpMessageConverter wireTextConverter) {
            List<HttpMessageConverter<?>> converters =
                    Arrays.<HttpMessageConverter<?>>asList(
                            new ByteArrayHttpMessageConverter(),
                            new StringHttpMessageConverter(),
                            wireTextConverter);
            RestTemplate restTemplate = new RestTemplate(converters);
            restTemplate.setRequestFactory(requestFactory);
            return restTemplate;
        }
    }

    static final class BudgetResponseFactory implements ClientHttpRequestFactory {
        private byte[] nextResponseBody;
        private MediaType nextResponseContentType;
        private byte[] lastRequestBody;

        void prepare(byte[] responseBody, MediaType contentType) {
            nextResponseBody = responseBody.clone();
            nextResponseContentType = contentType;
            lastRequestBody = null;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            return new MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected ClientHttpResponse executeInternal() {
                    if (nextResponseBody == null) {
                        throw new IllegalStateException("response was not prepared");
                    }
                    lastRequestBody = getBodyAsBytes();
                    MockClientHttpResponse response =
                            new MockClientHttpResponse(
                                    nextResponseBody,
                                    HttpStatus.OK);
                    response.getHeaders().setContentType(
                            nextResponseContentType);
                    response.getHeaders().setContentLength(
                            nextResponseBody.length);
                    return response;
                }
            };
        }

        byte[] getLastRequestBody() {
            return lastRequestBody;
        }
    }

    static final class WireText {
        private final String value;

        WireText(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    static final class WireTextHttpMessageConverter
            extends AbstractHttpMessageConverter<WireText> {
        private int readCount;

        WireTextHttpMessageConverter() {
            super(MediaType.TEXT_PLAIN);
        }

        @Override
        protected boolean supports(Class<?> type) {
            return WireText.class == type;
        }

        @Override
        protected WireText readInternal(
                Class<? extends WireText> type,
                HttpInputMessage inputMessage) throws IOException {
            readCount++;
            Charset charset = inputMessage.getHeaders()
                    .getContentType()
                    .getCharset();
            byte[] body = StreamUtils.copyToByteArray(inputMessage.getBody());
            return new WireText(new String(body, charset));
        }

        @Override
        protected void writeInternal(
                WireText value,
                HttpOutputMessage outputMessage) {
            throw new UnsupportedOperationException(
                    "response-only test converter");
        }

        int getReadCount() {
            return readCount;
        }
    }

}
