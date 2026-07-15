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
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

final class RestTemplateObservationRuntime {

    static final int DEFAULT_MAX_BODY_BYTES = 64 * 1024;

    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger("log.mask.http");

    private final HttpExchangeEventWriter eventWriter = new HttpExchangeEventWriter();
    private final boolean governanceEnabled;
    private final UntypedBodyJsonWriter untypedBodyJsonWriter;
    private final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>();

    RestTemplateObservationRuntime(boolean governanceEnabled) {
        this.governanceEnabled = governanceEnabled;
        this.untypedBodyJsonWriter =
                new UntypedBodyJsonWriter(DEFAULT_MAX_BODY_BYTES);
    }

    boolean isInfoEnabled() {
        return EVENT_LOGGER.isInfoEnabled();
    }

    boolean isGovernanceEnabled() {
        return governanceEnabled;
    }

    int maxBodyBytes() {
        return DEFAULT_MAX_BODY_BYTES;
    }

    ObservedBody writeStringBody(String value) {
        return untypedBodyJsonWriter.writeString(value);
    }

    ObservedBody writeByteArrayBody(byte[] value) {
        return untypedBodyJsonWriter.writeBytes(value);
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

    ExchangeScope open(HttpRequest request, byte[] wireBody) {
        ThreadState state = state();
        ObservedBody body = state.pendingRequestBodies.remove(request);
        if (body == null) {
            try {
                body = untypedBodyJsonWriter.writeWire(
                        wireBody,
                        request.getHeaders().getContentType());
            } catch (RuntimeException ignored) {
                body = untypedBodyJsonWriter.writeBytes(wireBody);
            }
        }
        ExchangeScope scope = new ExchangeScope(
                toEventRequest(request, body));
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

    void beginJacksonResponseRead() {
        ThreadState state = threadState.get();
        if (state == null || state.scopes.isEmpty()) {
            return;
        }
        state.scopes.peek().jacksonResponseReadStarted = true;
    }

    BoundedBodyCapture responseBodyCapture(ExchangeScope scope) {
        if (scope.responseCapture == null) {
            MediaType contentType = responseContentType(scope);
            scope.responseContentType = contentType;
            scope.responseCapture = new BoundedBodyCapture(
                    untypedBodyJsonWriter.maxWireBytes(contentType));
        }
        return scope.responseCapture;
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

    private HttpExchangeResponse toEventResponse(ExchangeScope scope)
            throws IOException {
        int status = scope.response.getRawStatusCode();
        ObservedBody body = responseBody(scope, status);
        return HttpExchangeResponse.builder()
                .status(status)
                .headers(io.github.summerwenlabs.log.mask.http.RegionState.SUCCESS,
                        toNameValues(scope.response.getHeaders()))
                .body(body.state(), body.value())
                .build();
    }

    private ObservedBody responseBody(ExchangeScope scope, int status) {
        if (scope.responseBody != null) {
            return scope.responseBody;
        }
        if (scope.jacksonResponseReadStarted) {
            return ObservedBody.processingFailed();
        }
        BoundedBodyCapture capture = scope.responseCapture;
        if (capture != null) {
            if (capture.isLimitExceeded()) {
                return ObservedBody.limitExceeded();
            }
            if (capture.hasBytes()) {
                return scope.responseContentTypeUnavailable
                        ? untypedBodyJsonWriter.writeBytes(capture.bytes())
                        : untypedBodyJsonWriter.writeWire(
                                capture.bytes(),
                                scope.responseContentType);
            }
            if (capture.hasNoUsableBytes()) {
                return ObservedBody.processingFailed();
            }
            if (capture.isConfirmedEmpty()) {
                return ObservedBody.absent();
            }
        }
        return definitelyHasNoResponseBody(scope, status)
                ? ObservedBody.absent()
                : untypedBodyJsonWriter.writeString("");
    }

    private MediaType responseContentType(ExchangeScope scope) {
        try {
            return scope.response.getHeaders().getContentType();
        } catch (RuntimeException ignored) {
            scope.responseContentTypeUnavailable = true;
            return null;
        }
    }

    private boolean definitelyHasNoResponseBody(ExchangeScope scope, int status) {
        if ("HEAD".equals(scope.requestMethod)
                || status >= 100 && status < 200
                || status == 204
                || status == 304) {
            return true;
        }
        try {
            return scope.response.getHeaders().getContentLength() == 0L;
        } catch (RuntimeException ignored) {
            return false;
        }
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
        private final String requestMethod;
        private ClientHttpResponse response;
        private ObservedBody responseBody;
        private BoundedBodyCapture responseCapture;
        private MediaType responseContentType;
        private boolean responseContentTypeUnavailable;
        private boolean jacksonResponseReadStarted;
        private long elapsedNanos;
        private boolean completed;

        private ExchangeScope(HttpExchangeRequest request) {
            this.request = request;
            this.requestMethod = request.getMethod();
        }
    }

    private static final class ThreadState {
        private final Map<HttpOutputMessage, ObservedBody> pendingRequestBodies =
                new IdentityHashMap<HttpOutputMessage, ObservedBody>();
        private final Deque<ExchangeScope> scopes = new ArrayDeque<ExchangeScope>();
    }
}
