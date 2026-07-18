/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseBodyTeeStreamContractIntegrationTest {

    private static final int DEFAULT_MAX_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class))
            .withUserConfiguration(StreamContractConfiguration.class);
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
    void repeatedGetBodyReturnsOneWrapperAndAccessesTheDelegateOnce() {
        byte[] responseBody = utf8("available but unconsumed");

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            StreamContractResponseFactory transport =
                    context.getBean(StreamContractResponseFactory.class);
            transport.prepare(responseBody);
            ResponseExtractor<InputStream[]> getBodyTwice = response ->
                    new InputStream[] {response.getBody(), response.getBody()};

            InputStream[] observedBodies = restTemplate.execute(
                    "https://api.example.com/repeated-get-body",
                    HttpMethod.GET,
                    null,
                    getBodyTwice);

            StreamContractClientHttpResponse rawResponse = transport.getLastResponse();
            assertNotNull(rawResponse);
            assertAll(
                    () -> assertSame(observedBodies[0], observedBodies[1]),
                    () -> assertNotSame(rawResponse.getRawBody(), observedBodies[0]),
                    () -> assertEquals(1, rawResponse.getBodyAccessCount()),
                    () -> assertEquals(0, rawResponse.getRawBody().getReadByteCount()),
                    () -> assertTrue(rawResponse.isClosed()));
        });

        assertSuccessfulTextBody(singleEvent().path("response"), "");
    }

    @Test
    void rereadingAfterResetDoesNotDuplicateTheCapturedPrefix() {
        byte[] responseBody = utf8("prefix|remaining");
        int prefixLength = utf8("prefix").length;

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            StreamContractResponseFactory transport =
                    context.getBean(StreamContractResponseFactory.class);
            transport.prepare(responseBody);
            ResponseExtractor<byte[][]> replayPrefix = response -> {
                InputStream body = response.getBody();
                assertTrue(body.markSupported());
                body.mark(prefixLength);
                byte[] firstRead = readExactly(body, prefixLength);
                body.reset();
                byte[] replayed = readExactly(body, prefixLength);
                return new byte[][] {firstRead, replayed};
            };

            byte[][] actual = restTemplate.execute(
                    "https://api.example.com/mark-reset",
                    HttpMethod.GET,
                    null,
                    replayPrefix);

            assertArrayEquals(utf8("prefix"), actual[0]);
            assertArrayEquals(actual[0], actual[1]);
            assertTrue(transport.getLastResponse().isClosed());
        });

        assertSuccessfulTextBody(singleEvent().path("response"), "prefix");
    }

    @Test
    void skipDelegatesWithoutReadsAndOnlyTheContinuousPrefixIsCaptured() {
        byte[] prefix = utf8("prefix");
        byte[] gap = utf8("|skipped-gap|");
        byte[] suffix = utf8("suffix");
        byte[] responseBody = utf8("prefix|skipped-gap|suffix");

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            StreamContractResponseFactory transport =
                    context.getBean(StreamContractResponseFactory.class);
            transport.prepare(responseBody);
            ResponseExtractor<byte[][]> readAroundGap = response -> {
                InputStream body = response.getBody();
                byte[] actualPrefix = readExactly(body, prefix.length);
                long skipped = body.skip(gap.length);
                assertEquals(gap.length, skipped);
                byte[] actualSuffix = readExactly(body, suffix.length);
                return new byte[][] {actualPrefix, actualSuffix};
            };

            byte[][] actual = restTemplate.execute(
                    "https://api.example.com/skip-gap",
                    HttpMethod.GET,
                    null,
                    readAroundGap);

            StreamContractCountingInputStream rawBody =
                    transport.getLastResponse().getRawBody();
            assertAll(
                    () -> assertArrayEquals(prefix, actual[0]),
                    () -> assertArrayEquals(suffix, actual[1]),
                    () -> assertEquals(prefix.length + suffix.length,
                            rawBody.getReadByteCount()),
                    () -> assertEquals(gap.length, rawBody.getSkippedByteCount()),
                    () -> assertEquals(1, rawBody.getSkipInvocationCount()),
                    () -> assertTrue(transport.getLastResponse().isClosed()));
        });

        assertSuccessfulTextBody(singleEvent().path("response"), "prefix");
    }

    @Test
    void skipFromTheStartWithoutASafePrefixIsProcessingFailed() {
        byte[] gap = utf8("skipped-gap|");
        byte[] suffix = utf8("visible-suffix");
        byte[] responseBody = utf8("skipped-gap|visible-suffix");

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            StreamContractResponseFactory transport =
                    context.getBean(StreamContractResponseFactory.class);
            transport.prepare(responseBody);
            ResponseExtractor<byte[]> readAfterGap = response -> {
                InputStream body = response.getBody();
                assertEquals(gap.length, body.skip(gap.length));
                return readExactly(body, suffix.length);
            };

            byte[] actual = restTemplate.execute(
                    "https://api.example.com/skip-without-prefix",
                    HttpMethod.GET,
                    null,
                    readAfterGap);

            StreamContractCountingInputStream rawBody =
                    transport.getLastResponse().getRawBody();
            assertAll(
                    () -> assertArrayEquals(suffix, actual),
                    () -> assertEquals(suffix.length, rawBody.getReadByteCount()),
                    () -> assertEquals(gap.length, rawBody.getSkippedByteCount()),
                    () -> assertEquals(1, rawBody.getSkipInvocationCount()));
        });

        JsonNode response = singleEvent().path("response");
        assertEquals("PROCESSING_FAILED", response.path("bodyState").textValue());
        assertEquals("", response.path("body").textValue());
    }

    @Test
    void unadornedExtractorReadsTheWholeOversizedResponseButTheLogCopyStops() {
        byte[] responseBody = repeatedBytes(DEFAULT_MAX_BODY_BYTES + 1024, (byte) 'x');

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            StreamContractResponseFactory transport =
                    context.getBean(StreamContractResponseFactory.class);
            transport.prepare(responseBody);
            ResponseExtractor<byte[]> readAll = response -> readFully(response.getBody());

            byte[] actual = restTemplate.execute(
                    "https://api.example.com/unadorned-oversized-extractor",
                    HttpMethod.GET,
                    null,
                    readAll);

            StreamContractClientHttpResponse rawResponse = transport.getLastResponse();
            assertAll(
                    () -> assertArrayEquals(responseBody, actual),
                    () -> assertEquals(1, rawResponse.getBodyAccessCount()),
                    () -> assertEquals(responseBody.length,
                            rawResponse.getRawBody().getReadByteCount()),
                    () -> assertTrue(rawResponse.isClosed()));
        });

        JsonNode response = singleEvent().path("response");
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

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read == -1) {
                throw new IOException("response ended before the requested prefix");
            }
            offset += read;
        }
        return result;
    }

    private static byte[] readFully(InputStream input) throws IOException {
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

    private static byte[] repeatedBytes(int size, byte value) {
        byte[] result = new byte[size];
        Arrays.fill(result, value);
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    static class StreamContractConfiguration {

        @Bean
        StreamContractResponseFactory streamContractResponseFactory() {
            return new StreamContractResponseFactory();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate streamContractRestTemplate(
                StreamContractResponseFactory requestFactory) {
            return new RestTemplate(requestFactory);
        }
    }

    static final class StreamContractResponseFactory implements ClientHttpRequestFactory {
        private byte[] nextResponseBody;
        private StreamContractClientHttpResponse lastResponse;

        void prepare(byte[] responseBody) {
            nextResponseBody = responseBody.clone();
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
                    lastResponse = new StreamContractClientHttpResponse(nextResponseBody);
                    return lastResponse;
                }
            };
        }

        StreamContractClientHttpResponse getLastResponse() {
            return lastResponse;
        }
    }

    static final class StreamContractClientHttpResponse implements ClientHttpResponse {
        private final StreamContractCountingInputStream body;
        private final HttpHeaders headers = new HttpHeaders();
        private int bodyAccessCount;
        private boolean closed;

        StreamContractClientHttpResponse(byte[] body) {
            this.body = new StreamContractCountingInputStream(body);
            headers.setContentType(new MediaType(
                    MediaType.TEXT_PLAIN.getType(),
                    MediaType.TEXT_PLAIN.getSubtype(),
                    StandardCharsets.UTF_8));
            headers.setContentLength(body.length);
        }

        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
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
                throw new AssertionError("stream contract body must close", exception);
            }
        }

        StreamContractCountingInputStream getRawBody() {
            return body;
        }

        int getBodyAccessCount() {
            return bodyAccessCount;
        }

        boolean isClosed() {
            return closed;
        }
    }

    static final class StreamContractCountingInputStream extends InputStream {
        private final byte[] source;
        private int position;
        private int markedPosition = -1;
        private int readByteCount;
        private int skippedByteCount;
        private int skipInvocationCount;

        StreamContractCountingInputStream(byte[] source) {
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
        public long skip(long count) {
            skipInvocationCount++;
            if (count <= 0) {
                return 0;
            }
            int skipped = (int) Math.min(count, source.length - position);
            position += skipped;
            skippedByteCount += skipped;
            return skipped;
        }

        @Override
        public int available() {
            return source.length - position;
        }

        @Override
        public synchronized void mark(int readLimit) {
            markedPosition = position;
        }

        @Override
        public synchronized void reset() throws IOException {
            if (markedPosition < 0) {
                throw new IOException("stream was not marked");
            }
            position = markedPosition;
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        int getReadByteCount() {
            return readByteCount;
        }

        int getSkippedByteCount() {
            return skippedByteCount;
        }

        int getSkipInvocationCount() {
            return skipInvocationCount;
        }
    }

}
