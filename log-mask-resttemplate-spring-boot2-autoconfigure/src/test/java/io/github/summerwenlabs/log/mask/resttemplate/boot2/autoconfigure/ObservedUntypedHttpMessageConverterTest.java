package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.client.MockClientHttpRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedUntypedHttpMessageConverterTest {

    @Test
    void capabilitiesMatchDelegates() {
        StringHttpMessageConverter stringDelegate =
                new StringHttpMessageConverter();
        ObservedStringHttpMessageConverter observedString =
                observed(stringDelegate);
        ByteArrayHttpMessageConverter byteArrayDelegate =
                new ByteArrayHttpMessageConverter();
        ObservedByteArrayHttpMessageConverter observedByteArray =
                observed(byteArrayDelegate);
        List<Class<?>> classes = Arrays.<Class<?>>asList(
                String.class,
                byte[].class,
                Object.class);
        List<MediaType> mediaTypes = Arrays.asList(
                null,
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_OCTET_STREAM);

        for (Class<?> candidate : classes) {
            for (MediaType mediaType : mediaTypes) {
                assertEquals(
                        stringDelegate.canRead(candidate, mediaType),
                        observedString.canRead(candidate, mediaType));
                assertEquals(
                        stringDelegate.canWrite(candidate, mediaType),
                        observedString.canWrite(candidate, mediaType));
                assertEquals(
                        byteArrayDelegate.canRead(candidate, mediaType),
                        observedByteArray.canRead(candidate, mediaType));
                assertEquals(
                        byteArrayDelegate.canWrite(candidate, mediaType),
                        observedByteArray.canWrite(candidate, mediaType));
            }
        }
    }

    @Test
    void supportedMediaTypesMatchDelegates() {
        StringHttpMessageConverter stringDelegate =
                new StringHttpMessageConverter();
        stringDelegate.setSupportedMediaTypes(Arrays.asList(
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_JSON));
        ObservedStringHttpMessageConverter observedString =
                observed(stringDelegate);
        ByteArrayHttpMessageConverter byteArrayDelegate =
                new ByteArrayHttpMessageConverter();
        byteArrayDelegate.setSupportedMediaTypes(Arrays.asList(
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.IMAGE_PNG));
        ObservedByteArrayHttpMessageConverter observedByteArray =
                observed(byteArrayDelegate);

        assertEquals(
                stringDelegate.getSupportedMediaTypes(),
                observedString.getSupportedMediaTypes());
        assertEquals(
                stringDelegate.getSupportedMediaTypes(String.class),
                observedString.getSupportedMediaTypes(String.class));
        assertEquals(
                stringDelegate.getSupportedMediaTypes(Object.class),
                observedString.getSupportedMediaTypes(Object.class));
        assertEquals(
                byteArrayDelegate.getSupportedMediaTypes(),
                observedByteArray.getSupportedMediaTypes());
        assertEquals(
                byteArrayDelegate.getSupportedMediaTypes(byte[].class),
                observedByteArray.getSupportedMediaTypes(byte[].class));
        assertEquals(
                byteArrayDelegate.getSupportedMediaTypes(Object.class),
                observedByteArray.getSupportedMediaTypes(Object.class));
    }

    @Test
    void stringWritePreservesDelegateCharsetAndWireResult() throws IOException {
        Charset charset = StandardCharsets.UTF_16LE;
        MediaType contentType = new MediaType(MediaType.TEXT_PLAIN, charset);
        String value = "contract-\u20ac";
        StringHttpMessageConverter delegate =
                new StringHttpMessageConverter(charset);
        RestTemplateObservationRuntime runtime =
                new RestTemplateObservationRuntime(false);
        ObservedStringHttpMessageConverter observed =
                new ObservedStringHttpMessageConverter(delegate, runtime);
        MockClientHttpRequest expected = request();
        MockClientHttpRequest actual = request();

        try {
            delegate.write(value, contentType, expected);
            observed.write(value, contentType, actual);

            assertSameWireResult(expected, actual);
            assertEquals(
                    charset,
                    actual.getHeaders().getContentType().getCharset());
            assertArrayEquals(value.getBytes(charset), actual.getBodyAsBytes());
        } finally {
            runtime.discardRequestBody(actual);
        }
    }

    @Test
    void byteArrayWritePreservesDelegateWireResult() throws IOException {
        byte[] value = new byte[] {0, 1, 2, 3, (byte) 0xff};
        ByteArrayHttpMessageConverter delegate =
                new ByteArrayHttpMessageConverter();
        RestTemplateObservationRuntime runtime =
                new RestTemplateObservationRuntime(false);
        ObservedByteArrayHttpMessageConverter observed =
                new ObservedByteArrayHttpMessageConverter(delegate, runtime);
        MockClientHttpRequest expected = request();
        MockClientHttpRequest actual = request();

        try {
            delegate.write(value, MediaType.APPLICATION_OCTET_STREAM, expected);
            observed.write(value, MediaType.APPLICATION_OCTET_STREAM, actual);

            assertSameWireResult(expected, actual);
            assertArrayEquals(value, actual.getBodyAsBytes());
        } finally {
            runtime.discardRequestBody(actual);
        }
    }

    @Test
    void stringReadReturnsDelegateValueWithDeclaredCharset() throws IOException {
        Charset charset = StandardCharsets.UTF_16BE;
        MediaType contentType = new MediaType(MediaType.TEXT_PLAIN, charset);
        String value = "response-\u20ac";
        byte[] wireBody = value.getBytes(charset);
        StringHttpMessageConverter delegate =
                new StringHttpMessageConverter(StandardCharsets.US_ASCII);
        ObservedStringHttpMessageConverter observed = observed(delegate);

        String expected = delegate.read(
                String.class,
                input(wireBody, contentType));
        String actual = observed.read(
                String.class,
                input(wireBody, contentType));

        assertEquals(expected, actual);
        assertEquals(value, actual);
    }

    @Test
    void byteArrayReadReturnsDelegateValue() throws IOException {
        byte[] wireBody = new byte[] {4, 3, 2, 1, 0, (byte) 0xff};
        ByteArrayHttpMessageConverter delegate =
                new ByteArrayHttpMessageConverter();
        ObservedByteArrayHttpMessageConverter observed = observed(delegate);

        byte[] expected = delegate.read(
                byte[].class,
                input(wireBody, MediaType.APPLICATION_OCTET_STREAM));
        byte[] actual = observed.read(
                byte[].class,
                input(wireBody, MediaType.APPLICATION_OCTET_STREAM));

        assertArrayEquals(expected, actual);
        assertArrayEquals(wireBody, actual);
    }

    @Test
    void stringReadPropagatesDelegateIOException() {
        IOException failure = new IOException("string read failed");
        ObservedStringHttpMessageConverter observed = observed(
                new StringHttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.read(
                        String.class,
                        new FailingInputMessage(
                                failure,
                                MediaType.TEXT_PLAIN)));

        assertSame(failure, thrown);
    }

    @Test
    void stringWritePropagatesDelegateIOException() {
        IOException failure = new IOException("string write failed");
        ObservedStringHttpMessageConverter observed = observed(
                new StringHttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.write(
                        "request",
                        MediaType.TEXT_PLAIN,
                        new FailingOutputMessage(failure)));

        assertSame(failure, thrown);
    }

    @Test
    void byteArrayReadPropagatesDelegateIOException() {
        IOException failure = new IOException("byte array read failed");
        ObservedByteArrayHttpMessageConverter observed = observed(
                new ByteArrayHttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.read(
                        byte[].class,
                        new FailingInputMessage(
                                failure,
                                MediaType.APPLICATION_OCTET_STREAM)));

        assertSame(failure, thrown);
    }

    @Test
    void byteArrayWritePropagatesDelegateIOException() {
        IOException failure = new IOException("byte array write failed");
        ObservedByteArrayHttpMessageConverter observed = observed(
                new ByteArrayHttpMessageConverter());

        IOException thrown = assertThrows(
                IOException.class,
                () -> observed.write(
                        new byte[] {1, 2, 3},
                        MediaType.APPLICATION_OCTET_STREAM,
                        new FailingOutputMessage(failure)));

        assertSame(failure, thrown);
    }

    private static ObservedStringHttpMessageConverter observed(
            StringHttpMessageConverter delegate) {
        return new ObservedStringHttpMessageConverter(
                delegate,
                new RestTemplateObservationRuntime(false));
    }

    private static ObservedByteArrayHttpMessageConverter observed(
            ByteArrayHttpMessageConverter delegate) {
        return new ObservedByteArrayHttpMessageConverter(
                delegate,
                new RestTemplateObservationRuntime(false));
    }

    private static MockHttpInputMessage input(
            byte[] body,
            MediaType contentType) {
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

    private static final class FailingInputMessage implements HttpInputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final IOException failure;

        private FailingInputMessage(
                IOException failure,
                MediaType contentType) {
            this.failure = failure;
            headers.setContentType(contentType);
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

    private static final class FailingOutputMessage
            implements HttpOutputMessage {
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
