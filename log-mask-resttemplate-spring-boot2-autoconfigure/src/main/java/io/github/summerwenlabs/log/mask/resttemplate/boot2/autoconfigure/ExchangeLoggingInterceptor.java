package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Opens and terminates observation around the downstream interceptor chain.
 *
 * <p>Duration covers downstream interceptors and transport only. When INFO is
 * disabled the interceptor performs a complete observation bypass. Any
 * downstream exception is rethrown unchanged after best-effort finalization.
 *
 * @author SummerWen
 * @since 0.1
 */
final class ExchangeLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final RestTemplateObservationRuntime runtime;

    ExchangeLoggingInterceptor(RestTemplateObservationRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        if (!runtime.isInfoEnabled()) {
            runtime.discardRequestBody(request);
            return execution.execute(request, body);
        }
        RestTemplateObservationRuntime.ExchangeScope scope = runtime.open(request, body);
        long startedAt = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long elapsedNanos = Math.max(0L, System.nanoTime() - startedAt);
            return runtime.response(scope, response, elapsedNanos);
        } catch (IOException | RuntimeException | Error failure) {
            long elapsedNanos = Math.max(0L, System.nanoTime() - startedAt);
            runtime.completeWithoutResponse(scope, elapsedNanos);
            throw failure;
        }
    }
}
