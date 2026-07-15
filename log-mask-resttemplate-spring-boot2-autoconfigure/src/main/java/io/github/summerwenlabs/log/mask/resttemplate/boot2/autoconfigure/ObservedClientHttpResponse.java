package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

final class ObservedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;
    private final RestTemplateObservationRuntime runtime;
    private final RestTemplateObservationRuntime.ExchangeScope scope;
    private boolean bodyResolved;
    private InputStream body;

    ObservedClientHttpResponse(
            ClientHttpResponse delegate,
            RestTemplateObservationRuntime runtime,
            RestTemplateObservationRuntime.ExchangeScope scope) {
        this.delegate = delegate;
        this.runtime = runtime;
        this.scope = scope;
    }

    @Override
    public HttpStatus getStatusCode() throws IOException {
        return delegate.getStatusCode();
    }

    @Override
    public int getRawStatusCode() throws IOException {
        return delegate.getRawStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return delegate.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public InputStream getBody() throws IOException {
        if (!runtime.isResponseBodyEnabled()) {
            return delegate.getBody();
        }
        if (!bodyResolved) {
            BoundedBodyCapture capture = runtime.responseBodyCapture(scope);
            try {
                InputStream delegateBody = delegate.getBody();
                if (delegateBody == null) {
                    capture.endOfInput();
                } else {
                    body = new ObservedBodyInputStream(delegateBody, capture);
                }
                bodyResolved = true;
            } catch (IOException | RuntimeException | Error failure) {
                capture.readFailed();
                throw failure;
            }
        }
        return body;
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } finally {
            runtime.complete(scope);
        }
    }

    RestTemplateObservationRuntime.ExchangeScope scope() {
        return scope;
    }
}
