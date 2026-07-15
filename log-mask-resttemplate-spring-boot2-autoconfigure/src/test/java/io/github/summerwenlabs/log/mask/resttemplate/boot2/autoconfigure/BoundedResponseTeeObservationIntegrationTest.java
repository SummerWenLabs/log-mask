package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedResponseTeeObservationIntegrationTest {

    private static final int DEFAULT_MAX_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class))
            .withUserConfiguration(ControlledTransportConfiguration.class);
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
    void responseConstructionHeadersAndCloseDoNotReadAnUnconsumedBody() {
        byte[] responseBody = utf8("available but deliberately unconsumed");

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);
            transport.prepare(responseBody, MediaType.TEXT_PLAIN);
            ResponseExtractor<Void> headersOnly = response -> {
                assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
                return null;
            };

            restTemplate.execute(
                    "https://api.example.com/unconsumed",
                    HttpMethod.GET,
                    null,
                    headersOnly);

            CountingClientHttpResponse response = transport.getLastResponse();
            assertNotNull(response);
            assertEquals(0, response.getBodyAccessCount());
            assertEquals(0, response.getReadByteCount());
            assertTrue(response.isClosed());
        });

        JsonNode response = singleEvent().path("response");
        assertEquals("SUCCESS", response.path("bodyState").textValue());
        assertTrue(response.path("body").isTextual());
        assertEquals("", response.path("body").textValue());
    }

    @Test
    void partiallyConsumedResponseLogsOnlyTheBytesReadByTheBusinessExtractor() {
        byte[] responseBody = utf8("prefix-visible|suffix-must-remain-unread");
        int prefixLength = utf8("prefix-visible").length;

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);
            transport.prepare(responseBody, MediaType.TEXT_PLAIN);
            ResponseExtractor<byte[]> prefixOnly = response -> {
                byte[] prefix = new byte[prefixLength];
                int read = response.getBody().read(prefix);
                assertEquals(prefixLength, read);
                return prefix;
            };

            byte[] actual = restTemplate.execute(
                    "https://api.example.com/partially-consumed",
                    HttpMethod.GET,
                    null,
                    prefixOnly);

            assertArrayEquals(Arrays.copyOf(responseBody, prefixLength), actual);
            CountingClientHttpResponse response = transport.getLastResponse();
            assertNotNull(response);
            assertEquals(1, response.getBodyAccessCount());
            assertEquals(prefixLength, response.getReadByteCount());
            assertTrue(response.isClosed());
        });

        JsonNode response = singleEvent().path("response");
        assertEquals("SUCCESS", response.path("bodyState").textValue());
        assertEquals("prefix-visible", response.path("body").textValue());
    }

    @Test
    void oversizedResponseRemainsCompleteWhileOnlyItsLogCopyExceedsTheLimit() {
        byte[] responseBody = repeatedBytes(DEFAULT_MAX_BODY_BYTES + 1024, (byte) 'x');
        String requestBody = "request-remains-observable";

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);
            transport.prepare(responseBody, MediaType.APPLICATION_OCTET_STREAM);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    "https://api.example.com/oversized-response-tee",
                    HttpMethod.POST,
                    new HttpEntity<String>(requestBody),
                    byte[].class);

            assertArrayEquals(responseBody, response.getBody());
            assertArrayEquals(utf8(requestBody), transport.getLastRequestBody());
            CountingClientHttpResponse rawResponse = transport.getLastResponse();
            assertNotNull(rawResponse);
            assertEquals(1, rawResponse.getBodyAccessCount());
            assertEquals(responseBody.length, rawResponse.getReadByteCount());
            assertTrue(rawResponse.isClosed());
        });

        JsonNode event = singleEvent();
        assertSuccessfulTextBody(event.path("request"), requestBody);
        JsonNode response = event.path("response");
        assertEquals("LIMIT_EXCEEDED", response.path("bodyState").textValue());
        assertEquals("", response.path("body").textValue());
    }

    private void assertSuccessfulTextBody(JsonNode region, String expected) {
        assertEquals("SUCCESS", region.path("bodyState").textValue());
        assertTrue(region.path("body").isTextual());
        assertEquals(expected, region.path("body").textValue());
    }

    private JsonNode singleEvent() {
        assertEquals(1, events.getEvents().size());
        try {
            return objectMapper.readTree(events.getEvents().get(0).getFormattedMessage());
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] repeatedBytes(int size, byte value) {
        byte[] result = new byte[size];
        Arrays.fill(result, value);
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    static class ControlledTransportConfiguration {

        @Bean
        ControlledResponseFactory controlledResponseFactory() {
            return new ControlledResponseFactory();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate controlledRestTemplate(ControlledResponseFactory requestFactory) {
            return new RestTemplate(requestFactory);
        }
    }

    static final class ControlledResponseFactory implements ClientHttpRequestFactory {
        private byte[] nextResponseBody;
        private MediaType nextResponseContentType;
        private byte[] lastRequestBody;
        private CountingClientHttpResponse lastResponse;

        void prepare(byte[] responseBody, MediaType contentType) {
            nextResponseBody = responseBody.clone();
            nextResponseContentType = contentType;
            lastRequestBody = null;
            lastResponse = null;
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
                    lastResponse = new CountingClientHttpResponse(
                            nextResponseBody,
                            nextResponseContentType);
                    return lastResponse;
                }
            };
        }

        byte[] getLastRequestBody() {
            return lastRequestBody;
        }

        CountingClientHttpResponse getLastResponse() {
            return lastResponse;
        }
    }

    static final class CountingClientHttpResponse implements ClientHttpResponse {
        private final CountingInputStream body;
        private final HttpHeaders headers = new HttpHeaders();
        private int bodyAccessCount;
        private boolean closed;

        CountingClientHttpResponse(byte[] body, MediaType contentType) {
            this.body = new CountingInputStream(body);
            headers.setContentType(contentType);
            headers.setContentLength(body.length);
        }

        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public int getRawStatusCode() {
            return HttpStatus.OK.value();
        }

        @Override
        public String getStatusText() {
            return HttpStatus.OK.getReasonPhrase();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            bodyAccessCount++;
            return body;
        }

        @Override
        public void close() {
            closed = true;
            try {
                body.close();
            } catch (IOException exception) {
                throw new AssertionError("counting response body must close", exception);
            }
        }

        int getBodyAccessCount() {
            return bodyAccessCount;
        }

        int getReadByteCount() {
            return body.getReadByteCount();
        }

        boolean isClosed() {
            return closed;
        }
    }

    static final class CountingInputStream extends InputStream {
        private final byte[] source;
        private int position;
        private int readByteCount;

        CountingInputStream(byte[] source) {
            this.source = source.clone();
        }

        @Override
        public int read() {
            if (position == source.length) {
                return -1;
            }
            readByteCount++;
            return source[position++] & 0xff;
        }

        @Override
        public int read(byte[] destination, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position == source.length) {
                return -1;
            }
            int copied = Math.min(length, source.length - position);
            System.arraycopy(source, position, destination, offset, copied);
            position += copied;
            readByteCount += copied;
            return copied;
        }

        @Override
        public int available() {
            return source.length - position;
        }

        int getReadByteCount() {
            return readByteCount;
        }
    }

}
