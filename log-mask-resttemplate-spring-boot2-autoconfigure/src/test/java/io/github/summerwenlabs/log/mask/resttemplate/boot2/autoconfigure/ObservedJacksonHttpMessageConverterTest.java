/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.client.MockClientHttpRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedJacksonHttpMessageConverterTest {

    private static final MediaType UTF_16_JSON =
            new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_16);
    private static final Type PAYLOAD_LIST_TYPE =
            new ParameterizedTypeReference<List<Payload>>() {
            }.getType();

    @Test
    void capabilitiesAndSupportedMediaTypesMatchDelegate() {
        MappingJackson2HttpMessageConverter delegate =
                new MappingJackson2HttpMessageConverter();
        ObservedJacksonHttpMessageConverter observed = observed(delegate);

        for (MediaType mediaType :
                Arrays.asList(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN)) {
            assertEquals(
                    delegate.canRead(Payload.class, mediaType),
                    observed.canRead(Payload.class, mediaType));
            assertEquals(
                    delegate.canRead(PAYLOAD_LIST_TYPE, null, mediaType),
                    observed.canRead(PAYLOAD_LIST_TYPE, null, mediaType));
            assertEquals(
                    delegate.canWrite(Payload.class, mediaType),
                    observed.canWrite(Payload.class, mediaType));
            assertEquals(
                    delegate.canWrite(PAYLOAD_LIST_TYPE, List.class, mediaType),
                    observed.canWrite(PAYLOAD_LIST_TYPE, List.class, mediaType));
        }
        assertEquals(
                delegate.getSupportedMediaTypes(),
                observed.getSupportedMediaTypes());
        assertEquals(
                delegate.getSupportedMediaTypes(Payload.class),
                observed.getSupportedMediaTypes(Payload.class));
    }

    @Test
    void ordinaryReadReturnsDelegateResult() throws IOException {
        MappingJackson2HttpMessageConverter delegate =
                new MappingJackson2HttpMessageConverter();
        ObservedJacksonHttpMessageConverter observed = observed(delegate);
        byte[] json = "{\"value\":\"ordinary\"}"
                .getBytes(StandardCharsets.UTF_16);

        Payload expected = (Payload) delegate.read(
                Payload.class,
                input(json, UTF_16_JSON));
        Payload actual = (Payload) observed.read(
                Payload.class,
                input(json, UTF_16_JSON));

        assertEquals(expected.getValue(), actual.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void genericReadReturnsDelegateResult() throws IOException {
        MappingJackson2HttpMessageConverter delegate =
                new MappingJackson2HttpMessageConverter();
        ObservedJacksonHttpMessageConverter observed = observed(delegate);
        byte[] json = "[{\"value\":\"generic\"}]"
                .getBytes(StandardCharsets.UTF_8);

        List<Payload> expected = (List<Payload>) delegate.read(
                PAYLOAD_LIST_TYPE,
                null,
                jsonInput(json));
        List<Payload> actual = (List<Payload>) observed.read(
                PAYLOAD_LIST_TYPE,
                null,
                jsonInput(json));

        assertEquals(expected.size(), actual.size());
        assertEquals(expected.get(0).getValue(), actual.get(0).getValue());
    }

    @Test
    void ordinaryWritePreservesDelegateWireResult() throws IOException {
        MappingJackson2HttpMessageConverter delegate =
                new MappingJackson2HttpMessageConverter();
        RestTemplateObservationRuntime runtime =
                new RestTemplateObservationRuntime(false);
        ObservedJacksonHttpMessageConverter observed =
                new ObservedJacksonHttpMessageConverter(delegate, runtime);
        Payload payload = new Payload("ordinary");
        MockClientHttpRequest expected = request();
        MockClientHttpRequest actual = request();

        try {
            delegate.write(payload, UTF_16_JSON, expected);
            observed.write(payload, UTF_16_JSON, actual);

            assertSameWireResult(expected, actual);
        } finally {
            runtime.discardRequestBody(actual);
        }
    }

    @Test
    void genericWritePreservesDelegateWireResult() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(MapperFeature.USE_STATIC_TYPING);
        MappingJackson2HttpMessageConverter delegate =
                new MappingJackson2HttpMessageConverter(objectMapper);
        RestTemplateObservationRuntime runtime =
                new RestTemplateObservationRuntime(false);
        ObservedJacksonHttpMessageConverter observed =
                new ObservedJacksonHttpMessageConverter(delegate, runtime);
        List<Payload> payload = Collections.<Payload>singletonList(
                new ExtendedPayload("generic", "subtype-only"));
        MockClientHttpRequest expected = request();
        MockClientHttpRequest actual = request();

        try {
            delegate.write(
                    payload,
                    PAYLOAD_LIST_TYPE,
                    MediaType.APPLICATION_JSON,
                    expected);
            observed.write(
                    payload,
                    PAYLOAD_LIST_TYPE,
                    MediaType.APPLICATION_JSON,
                    actual);

            assertSameWireResult(expected, actual);
        } finally {
            runtime.discardRequestBody(actual);
        }
    }

    @Test
    void ordinaryReadPropagatesDelegateIOException() {
        IOException failure = new IOException("ordinary read failed");
        ObservedJacksonHttpMessageConverter observed = observed(
                new MappingJackson2HttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.read(
                        Payload.class,
                        new FailingInputMessage(failure)));

        assertSame(failure, thrown);
    }

    @Test
    void genericReadPropagatesDelegateIOException() {
        IOException failure = new IOException("generic read failed");
        ObservedJacksonHttpMessageConverter observed = observed(
                new MappingJackson2HttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.read(
                        PAYLOAD_LIST_TYPE,
                        null,
                        new FailingInputMessage(failure)));

        assertSame(failure, thrown);
    }

    @Test
    void ordinaryWritePropagatesDelegateIOException() {
        IOException failure = new IOException("ordinary write failed");
        ObservedJacksonHttpMessageConverter observed = observed(
                new MappingJackson2HttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.write(
                        new Payload("ordinary"),
                        MediaType.APPLICATION_JSON,
                        new FailingOutputMessage(failure)));

        assertSame(failure, thrown);
    }

    @Test
    void genericWritePropagatesDelegateIOException() {
        IOException failure = new IOException("generic write failed");
        ObservedJacksonHttpMessageConverter observed = observed(
                new MappingJackson2HttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.write(
                        Collections.singletonList(new Payload("generic")),
                        PAYLOAD_LIST_TYPE,
                        MediaType.APPLICATION_JSON,
                        new FailingOutputMessage(failure)));

        assertSame(failure, thrown);
    }

    private static ObservedJacksonHttpMessageConverter observed(
            MappingJackson2HttpMessageConverter delegate) {
        return new ObservedJacksonHttpMessageConverter(
                delegate,
                new RestTemplateObservationRuntime(false));
    }

    private static MockHttpInputMessage jsonInput(byte[] json) {
        return input(json, MediaType.APPLICATION_JSON);
    }

    private static MockHttpInputMessage input(byte[] body, MediaType contentType) {
        MockHttpInputMessage input = new MockHttpInputMessage(body);
        input.getHeaders().setContentType(contentType);
        return input;
    }

    private static MockClientHttpRequest request() {
        return new MockClientHttpRequest(
                HttpMethod.POST,
                URI.create("https://api.example.com/contract"));
    }

    private static void assertSameWireResult(
            MockClientHttpRequest expected,
            MockClientHttpRequest actual) {
        assertEquals(expected.getHeaders(), actual.getHeaders());
        assertArrayEquals(expected.getBodyAsBytes(), actual.getBodyAsBytes());
    }

    static class Payload {
        private String value;

        Payload() {
        }

        Payload(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static final class ExtendedPayload extends Payload {
        private final String subtypeOnly;

        ExtendedPayload(String value, String subtypeOnly) {
            super(value);
            this.subtypeOnly = subtypeOnly;
        }

        public String getSubtypeOnly() {
            return subtypeOnly;
        }
    }

    private static final class FailingInputMessage implements HttpInputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final IOException failure;

        private FailingInputMessage(IOException failure) {
            this.failure = failure;
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        @Override
        public InputStream getBody() throws IOException {
            throw failure;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    private static final class FailingOutputMessage implements HttpOutputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final IOException failure;

        private FailingOutputMessage(IOException failure) {
            this.failure = failure;
        }

        @Override
        public OutputStream getBody() throws IOException {
            throw failure;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }
}
