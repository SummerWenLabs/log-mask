package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.github.summerwenlabs.log.mask.http.HttpExchangeEvent;
import io.github.summerwenlabs.log.mask.http.HttpExchangeEventWriter;
import io.github.summerwenlabs.log.mask.http.HttpExchangeRequest;
import io.github.summerwenlabs.log.mask.http.HttpExchangeResponse;
import io.github.summerwenlabs.log.mask.http.HttpRequestUri;
import io.github.summerwenlabs.log.mask.http.JsonValue;
import io.github.summerwenlabs.log.mask.http.NameValueCollection;
import io.github.summerwenlabs.log.mask.http.RegionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

final class BodylessExchangeLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger("log.mask.http");

    private final HttpExchangeEventWriter eventWriter = new HttpExchangeEventWriter();

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        HttpExchangeRequest eventRequest = toEventRequest(request);
        long startedAt = System.nanoTime();
        ClientHttpResponse response = null;
        try {
            response = execution.execute(request, body);
            return response;
        } finally {
            long elapsedNanos = System.nanoTime() - startedAt;
            emitSafely(eventRequest, response, Math.max(0L, elapsedNanos));
        }
    }

    private void emitSafely(
            HttpExchangeRequest request,
            ClientHttpResponse response,
            long elapsedNanos) {
        try {
            HttpExchangeEvent event = HttpExchangeEvent.builder()
                    .timestamp(Instant.now())
                    .exchangeId(UUID.randomUUID())
                    .durationMs(TimeUnit.NANOSECONDS.toMillis(elapsedNanos))
                    .governanceEnabled(true)
                    .request(request)
                    .response(response == null ? null : toEventResponse(response))
                    .build();
            EVENT_LOGGER.info(eventWriter.write(event));
        } catch (IOException | RuntimeException ignored) {
            // Observation must not replace or interrupt the application exchange.
        }
    }

    private static HttpExchangeRequest toEventRequest(HttpRequest request) {
        return HttpExchangeRequest.builder()
                .method(request.getMethodValue())
                .uri(HttpRequestUri.from(request.getURI()))
                .headers(RegionState.SUCCESS, toNameValues(request.getHeaders()))
                .body(RegionState.SUCCESS, JsonValue.nullValue())
                .build();
    }

    private static HttpExchangeResponse toEventResponse(ClientHttpResponse response)
            throws IOException {
        return HttpExchangeResponse.builder()
                .status(response.getRawStatusCode())
                .headers(RegionState.SUCCESS, toNameValues(response.getHeaders()))
                .body(RegionState.SUCCESS, JsonValue.nullValue())
                .build();
    }

    private static NameValueCollection toNameValues(HttpHeaders headers) {
        NameValueCollection.Builder result = NameValueCollection.builder();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            result.addAll(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return result.build();
    }
}
