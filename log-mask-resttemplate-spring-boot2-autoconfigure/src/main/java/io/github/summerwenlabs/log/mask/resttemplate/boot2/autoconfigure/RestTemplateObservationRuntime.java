package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
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
import io.github.summerwenlabs.log.mask.http.NameValueCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

final class RestTemplateObservationRuntime {

    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger("log.mask.http");

    private final HttpExchangeEventWriter eventWriter = new HttpExchangeEventWriter();
    private final boolean governanceEnabled;
    private final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>();

    RestTemplateObservationRuntime(boolean governanceEnabled) {
        this.governanceEnabled = governanceEnabled;
    }

    boolean isInfoEnabled() {
        return EVENT_LOGGER.isInfoEnabled();
    }

    boolean isGovernanceEnabled() {
        return governanceEnabled;
    }

    void offerRequestBody(HttpOutputMessage outputMessage, ObservedBody body) {
        state().pendingRequestBodies.put(outputMessage, body);
    }

    void discardRequestBody(HttpRequest request) {
        ThreadState state = threadState.get();
        if (state == null) {
            return;
        }
        state.pendingRequestBodies.remove(request);
        removeIfEmpty(state);
    }

    ExchangeScope open(HttpRequest request) {
        ThreadState state = state();
        ObservedBody body = state.pendingRequestBodies.remove(request);
        ExchangeScope scope = new ExchangeScope(
                toEventRequest(request, body == null ? ObservedBody.absent() : body));
        state.scopes.push(scope);
        return scope;
    }

    ClientHttpResponse response(
            ExchangeScope scope,
            ClientHttpResponse response,
            long elapsedNanos) {
        scope.response = response;
        scope.elapsedNanos = elapsedNanos;
        return new ObservedClientHttpResponse(response, this, scope);
    }

    void completeWithoutResponse(ExchangeScope scope, long elapsedNanos) {
        scope.elapsedNanos = elapsedNanos;
        complete(scope);
    }

    void recordResponseBody(ObservedBody body) {
        ThreadState state = threadState.get();
        if (state == null || state.scopes.isEmpty()) {
            return;
        }
        state.scopes.peek().responseBody = body;
    }

    void complete(ExchangeScope scope) {
        if (scope.completed) {
            return;
        }
        scope.completed = true;
        try {
            emit(scope);
        } catch (IOException | RuntimeException ignored) {
            // Observation must not replace or interrupt the application exchange.
        } finally {
            ThreadState state = threadState.get();
            if (state != null) {
                state.scopes.remove(scope);
                removeIfEmpty(state);
            }
        }
    }

    private void emit(ExchangeScope scope) throws IOException {
        HttpExchangeEvent event = HttpExchangeEvent.builder()
                .timestamp(Instant.now())
                .exchangeId(UUID.randomUUID())
                .durationMs(TimeUnit.NANOSECONDS.toMillis(
                        Math.max(0L, scope.elapsedNanos)))
                .governanceEnabled(governanceEnabled)
                .request(scope.request)
                .response(scope.response == null ? null : toEventResponse(scope))
                .build();
        EVENT_LOGGER.info(eventWriter.write(event));
    }

    private ThreadState state() {
        ThreadState state = threadState.get();
        if (state == null) {
            state = new ThreadState();
            threadState.set(state);
        }
        return state;
    }

    private void removeIfEmpty(ThreadState state) {
        if (state.pendingRequestBodies.isEmpty() && state.scopes.isEmpty()) {
            threadState.remove();
        }
    }

    private static HttpExchangeRequest toEventRequest(HttpRequest request, ObservedBody body) {
        return HttpExchangeRequest.builder()
                .method(request.getMethodValue())
                .uri(HttpRequestUri.from(request.getURI()))
                .headers(io.github.summerwenlabs.log.mask.http.RegionState.SUCCESS,
                        toNameValues(request.getHeaders()))
                .body(body.state(), body.value())
                .build();
    }

    private static HttpExchangeResponse toEventResponse(ExchangeScope scope)
            throws IOException {
        ObservedBody body = scope.responseBody == null
                ? ObservedBody.absent()
                : scope.responseBody;
        return HttpExchangeResponse.builder()
                .status(scope.response.getRawStatusCode())
                .headers(io.github.summerwenlabs.log.mask.http.RegionState.SUCCESS,
                        toNameValues(scope.response.getHeaders()))
                .body(body.state(), body.value())
                .build();
    }

    private static NameValueCollection toNameValues(HttpHeaders headers) {
        NameValueCollection.Builder result = NameValueCollection.builder();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            result.addAll(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return result.build();
    }

    static final class ExchangeScope {
        private final HttpExchangeRequest request;
        private ClientHttpResponse response;
        private ObservedBody responseBody;
        private long elapsedNanos;
        private boolean completed;

        private ExchangeScope(HttpExchangeRequest request) {
            this.request = request;
        }
    }

    private static final class ThreadState {
        private final Map<HttpOutputMessage, ObservedBody> pendingRequestBodies =
                new IdentityHashMap<HttpOutputMessage, ObservedBody>();
        private final Deque<ExchangeScope> scopes = new ArrayDeque<ExchangeScope>();
    }
}
