package io.github.summerwenlabs.log.mask.samples;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Supplies a request that always fails before a response can be obtained.
 *
 * @author SummerWen
 * @since 0.1
 */
final class FailingClientHttpRequestFactory implements ClientHttpRequestFactory {

    private final IOException failure = new IOException("sample transport failure");

    IOException failure() {
        return failure;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
        return new AbstractClientHttpRequest() {
            @Override
            public HttpMethod getMethod() {
                return httpMethod;
            }

            @Override
            public String getMethodValue() {
                return httpMethod.name();
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            protected OutputStream getBodyInternal(HttpHeaders headers) {
                return new ByteArrayOutputStream();
            }

            @Override
            protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                throw failure;
            }
        };
    }
}
