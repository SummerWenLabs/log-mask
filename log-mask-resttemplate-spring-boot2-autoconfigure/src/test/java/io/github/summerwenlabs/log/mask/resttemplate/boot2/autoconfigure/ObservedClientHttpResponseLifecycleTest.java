package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedClientHttpResponseLifecycleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
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
    void endOfInputDoesNotCompleteUntilTheResponseClosesExactlyOnce() throws Exception {
        byte[] payload = utf8("complete-response");
        CloseTrackingInputStream rawBody = new CloseTrackingInputStream(payload);
        ClientHttpResponse response = observedResponse(rawBody, payload.length);

        assertArrayEquals(payload, readFully(response.getBody()));
        assertEquals(payload.length, rawBody.getReadByteCount());
        assertOnlyResponseCloseCompletes(response);

        assertSuccessfulTextBody(singleEvent().path("response"), "complete-response");
    }

    @Test
    void bodyStreamCloseDoesNotCompleteAndDoesNotDrainUnreadBytes() throws Exception {
        byte[] payload = utf8("prefix|unread-tail");
        byte[] prefix = utf8("prefix");
        CloseTrackingInputStream rawBody = new CloseTrackingInputStream(payload);
        ClientHttpResponse response = observedResponse(rawBody, payload.length);
        InputStream observedBody = response.getBody();

        assertArrayEquals(prefix, readExactly(observedBody, prefix.length));
        observedBody.close();

        assertTrue(rawBody.isClosed());
        assertEquals(prefix.length, rawBody.getReadByteCount());
        assertOnlyResponseCloseCompletes(response);
        assertEquals(prefix.length, rawBody.getReadByteCount());

        assertSuccessfulTextBody(singleEvent().path("response"), "prefix");
    }

    @Test
    void readFailurePreservesTheSameExceptionAndTheSafePrefix() throws Exception {
        byte[] prefix = utf8("safe-prefix");
        IOException failure = new IOException("controlled response read failure");
        ClientHttpResponse response = observedResponse(
                new FailingInputStream(prefix, failure),
                prefix.length + 1L);
        InputStream observedBody = response.getBody();

        assertArrayEquals(prefix, readExactly(observedBody, prefix.length));
        IOException actual = assertThrows(IOException.class, observedBody::read);

        assertSame(failure, actual);
        assertOnlyResponseCloseCompletes(response);
        assertSuccessfulTextBody(singleEvent().path("response"), "safe-prefix");
    }

    @Test
    void readFailureBeforeAnyByteProducesProcessingFailedOnResponseClose()
            throws Exception {
        IOException failure = new IOException("controlled immediate read failure");
        ClientHttpResponse response = observedResponse(
                new FailingInputStream(new byte[0], failure),
                1L);

        IOException actual = assertThrows(IOException.class, response.getBody()::read);

        assertSame(failure, actual);
        assertOnlyResponseCloseCompletes(response);
        JsonNode responseRegion = singleEvent().path("response");
        assertEquals("PROCESSING_FAILED", responseRegion.path("bodyState").textValue());
        assertEquals("", responseRegion.path("body").textValue());
    }

    private ClientHttpResponse observedResponse(InputStream body, long contentLength) {
        RestTemplateObservationRuntime runtime = new RestTemplateObservationRuntime(true);
        MockClientHttpRequest request = new MockClientHttpRequest(
                HttpMethod.GET,
                URI.create("https://api.example.com/low-level-response"));
        RestTemplateObservationRuntime.ExchangeScope scope =
                runtime.open(request, new byte[0]);
        ClientHttpResponse delegate = new ControlledClientHttpResponse(body, contentLength);
        return runtime.response(scope, delegate, 0L);
    }

    private void assertOnlyResponseCloseCompletes(ClientHttpResponse response) {
        assertTrue(events.getEvents().isEmpty());

        response.close();
        assertEquals(1, events.getEvents().size());

        response.close();
        assertEquals(1, events.getEvents().size());
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
                throw new IOException("response ended before the expected bytes were read");
            }
            offset += read;
        }
        return result;
    }

    private static byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[32];
        int read;
        while ((read = input.read(buffer)) != -1) {
            result.write(buffer, 0, read);
        }
        return result.toByteArray();
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class ControlledClientHttpResponse implements ClientHttpResponse {
        private final InputStream body;
        private final HttpHeaders headers = new HttpHeaders();

        private ControlledClientHttpResponse(InputStream body, long contentLength) {
            this.body = body;
            headers.setContentType(new MediaType(
                    MediaType.TEXT_PLAIN.getType(),
                    MediaType.TEXT_PLAIN.getSubtype(),
                    StandardCharsets.UTF_8));
            headers.setContentLength(contentLength);
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
            return body;
        }

        @Override
        public void close() {
            try {
                body.close();
            } catch (IOException exception) {
                throw new AssertionError("controlled response body must close", exception);
            }
        }
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {
        private int readByteCount;
        private boolean closed;

        private CloseTrackingInputStream(byte[] source) {
            super(source);
        }

        @Override
        public synchronized int read() {
            int value = super.read();
            if (value >= 0) {
                readByteCount++;
            }
            return value;
        }

        @Override
        public synchronized int read(byte[] destination, int offset, int length) {
            int read = super.read(destination, offset, length);
            if (read > 0) {
                readByteCount += read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private int getReadByteCount() {
            return readByteCount;
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static final class FailingInputStream extends InputStream {
        private final byte[] prefix;
        private final IOException failure;
        private int position;

        private FailingInputStream(byte[] prefix, IOException failure) {
            this.prefix = prefix.clone();
            this.failure = failure;
        }

        @Override
        public int read() throws IOException {
            if (position == prefix.length) {
                throw failure;
            }
            return prefix[position++] & 0xff;
        }

        @Override
        public int read(byte[] destination, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (position == prefix.length) {
                throw failure;
            }
            int copied = Math.min(length, prefix.length - position);
            System.arraycopy(prefix, position, destination, offset, copied);
            position += copied;
            return copied;
        }
    }

}
