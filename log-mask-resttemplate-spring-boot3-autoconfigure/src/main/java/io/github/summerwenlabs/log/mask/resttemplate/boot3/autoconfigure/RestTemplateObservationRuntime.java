/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

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

import io.github.summerwenlabs.log.mask.http.NameValueCollection;
import io.github.summerwenlabs.log.mask.http.RegionState;
import io.github.summerwenlabs.log.mask.http.exchange.HttpExchangeEvent;
import io.github.summerwenlabs.log.mask.http.exchange.HttpExchangeEventWriter;
import io.github.summerwenlabs.log.mask.http.exchange.HttpExchangeRequest;
import io.github.summerwenlabs.log.mask.http.exchange.HttpExchangeResponse;
import io.github.summerwenlabs.log.mask.http.governance.HttpHeaderGovernance;
import io.github.summerwenlabs.log.mask.http.governance.HttpRequestUri;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Coordinates request snapshots, response observation, and terminal events.
 *
 * <p>Per-thread state uses an identity map for converter-to-request handoff and
 * a LIFO stack for synchronous nested exchanges. Scopes never cross threads,
 * are completed at most once, and are removed even when event rendering fails.
 * Observation failures are contained so application values and exceptions stay
 * unchanged.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RestTemplateObservationRuntime {

    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger("log.mask.http");

    private final RestTemplateObservationSettings settings;
    private final HttpExchangeEventWriter eventWriter;
    private final UntypedBodyJsonWriter untypedBodyJsonWriter;
    private final ThreadLocal<ThreadState> threadState = new ThreadLocal<ThreadState>();

    RestTemplateObservationRuntime(RestTemplateObservationSettings settings) {
        this.settings = settings;
        this.eventWriter = new HttpExchangeEventWriter(
                settings.getNameValueShape(),
                settings.isUriDetailsEnabled());
        this.untypedBodyJsonWriter =
                new UntypedBodyJsonWriter(settings.getMaxBodyBytes());
    }

    RestTemplateObservationRuntime(boolean governanceEnabled) {
        this(RestTemplateObservationSettings.defaults(governanceEnabled));
    }

    boolean isInfoEnabled() {
        return EVENT_LOGGER.isInfoEnabled();
    }

    boolean isGovernanceEnabled() {
        return settings.isGovernanceEnabled();
    }

    int maxBodyBytes() {
        return settings.getMaxBodyBytes();
    }

    MaskStrategyRegistry strategyRegistry() {
        return settings.getStrategyRegistry();
    }

    boolean isRequestBodyEnabled() {
        return settings.isRequestBodyEnabled();
    }

    boolean isResponseBodyEnabled() {
        return settings.isResponseBodyEnabled();
    }

    ObservedBody writeStringBody(String value) {
        return untypedBodyJsonWriter.writeString(value);
    }

    ObservedBody writeByteArrayBody(byte[] value) {
        return untypedBodyJsonWriter.writeBytes(value);
    }

    void offerRequestBody(HttpOutputMessage outputMessage, ObservedBody body) {
        if (!settings.isRequestBodyEnabled()) {
            return;
        }
        state().pendingRequestBodies.put(outputMessage, body);
    }

    void requestConverterFailed(HttpOutputMessage outputMessage) {
        if (!isInfoEnabled() || !(outputMessage instanceof HttpRequest)) {
            return;
        }
        try {
            HttpRequest request = (HttpRequest) outputMessage;
            ExchangeScope scope = new ExchangeScope(
                    toEventRequest(
                            request,
                            settings.isRequestBodyEnabled()
                                    ? ObservedBody.processingFailed()
                                    : ObservedBody.disabled()),
                    traceId());
            complete(scope);
        } catch (RuntimeException | Error ignored) {
            // Converter failures must keep their original application exception.
        }
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
        ObservedBody body = settings.isRequestBodyEnabled()
                ? state.pendingRequestBodies.remove(request)
                : ObservedBody.disabled();
        if (settings.isRequestBodyEnabled() && body == null) {
            try {
                body = untypedBodyJsonWriter.writeWire(
                        wireBody,
                        request.getHeaders().getContentType());
            } catch (RuntimeException ignored) {
                body = untypedBodyJsonWriter.writeBytes(wireBody);
            }
        }
        ExchangeScope scope = new ExchangeScope(
                toEventRequest(request, body),
                traceId());
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
        if (!settings.isResponseBodyEnabled()) {
            return;
        }
        ThreadState state = threadState.get();
        if (state == null || state.scopes.isEmpty()) {
            return;
        }
        state.scopes.peek().responseBody = body;
    }

    void recordResponseBody(HttpInputMessage inputMessage, ObservedBody body) {
        ExchangeScope scope = scopeFor(inputMessage);
        if (scope != null) {
            if (settings.isResponseBodyEnabled()) {
                scope.responseBody = body;
            }
            return;
        }
        recordResponseBody(body);
    }

    void beginJacksonResponseRead() {
        if (!settings.isResponseBodyEnabled()) {
            return;
        }
        ThreadState state = threadState.get();
        if (state == null || state.scopes.isEmpty()) {
            return;
        }
        state.scopes.peek().jacksonResponseReadStarted = true;
    }

    void beginJacksonResponseRead(HttpInputMessage inputMessage) {
        ExchangeScope scope = scopeFor(inputMessage);
        if (scope != null) {
            if (settings.isResponseBodyEnabled()) {
                scope.jacksonResponseReadStarted = true;
            }
            return;
        }
        beginJacksonResponseRead();
    }

    BoundedBodyCapture responseBodyCapture(ExchangeScope scope) {
        if (!settings.isResponseBodyEnabled()) {
            throw new IllegalStateException("Response body capture is disabled");
        }
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
                .traceId(scope.traceId)
                .governanceEnabled(settings.isGovernanceEnabled())
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

    private HttpExchangeRequest toEventRequest(HttpRequest request, ObservedBody body) {
        HttpRequestUri uri = settings.isGovernanceEnabled()
                ? settings.getPathGovernance().govern(
                        request.getURI(),
                        request.getMethod().name(),
                        settings.getQueryGovernance())
                : HttpRequestUri.from(request.getURI());
        HttpExchangeRequest.Builder eventRequest = HttpExchangeRequest.builder()
                .method(request.getMethod().name())
                .uri(uri)
                .body(body.state(), body.value());
        if (!settings.isRequestHeadersEnabled()) {
            return eventRequest.headers(RegionState.DISABLED, null).build();
        }
        NameValueCollection headers = toNameValues(request.getHeaders());
        if (!settings.isGovernanceEnabled()) {
            return eventRequest.headers(RegionState.SUCCESS, headers).build();
        }
        HttpHeaderGovernance.Result governed =
                settings.getRequestHeaderGovernance().govern(uri.getHost(), headers);
        return eventRequest.headers(governed.getState(), governed.getHeaders()).build();
    }

    private HttpExchangeResponse toEventResponse(ExchangeScope scope)
            throws IOException {
        int status = scope.response.getStatusCode().value();
        ObservedBody body = responseBody(scope, status);
        HttpExchangeResponse.Builder eventResponse = HttpExchangeResponse.builder()
                .status(status)
                .body(body.state(), body.value());
        if (!settings.isResponseHeadersEnabled()) {
            return eventResponse.headers(RegionState.DISABLED, null).build();
        }
        NameValueCollection headers = toNameValues(scope.response.getHeaders());
        if (!settings.isGovernanceEnabled()) {
            return eventResponse.headers(RegionState.SUCCESS, headers).build();
        }
        HttpHeaderGovernance.Result governed =
                settings.getResponseHeaderGovernance().govern(
                        scope.request.getUri().getHost(), headers);
        return eventResponse.headers(governed.getState(), governed.getHeaders()).build();
    }

    private ObservedBody responseBody(ExchangeScope scope, int status) {
        if (!settings.isResponseBodyEnabled()) {
            return ObservedBody.disabled();
        }
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

    private String traceId() {
        if (!settings.isTraceIdEnabled()) {
            return null;
        }
        for (String key : settings.getTraceIdMdcKeys()) {
            String value = MDC.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static ExchangeScope scopeFor(HttpInputMessage inputMessage) {
        return inputMessage instanceof ObservedClientHttpResponse
                ? ((ObservedClientHttpResponse) inputMessage).scope()
                : null;
    }

    private static NameValueCollection toNameValues(HttpHeaders headers) {
        NameValueCollection.Builder result = NameValueCollection.builder();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            result.addAll(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return result.build();
    }

    /** Holds mutable facts owned by exactly one synchronous exchange lifecycle. */
    static final class ExchangeScope {
        private final HttpExchangeRequest request;
        private final String requestMethod;
        private final String traceId;
        private ClientHttpResponse response;
        private ObservedBody responseBody;
        private BoundedBodyCapture responseCapture;
        private MediaType responseContentType;
        private boolean responseContentTypeUnavailable;
        private boolean jacksonResponseReadStarted;
        private long elapsedNanos;
        private boolean completed;

        private ExchangeScope(HttpExchangeRequest request, String traceId) {
            this.request = request;
            this.requestMethod = request.getMethod();
            this.traceId = traceId;
        }
    }

    private static final class ThreadState {
        private final Map<HttpOutputMessage, ObservedBody> pendingRequestBodies =
                new IdentityHashMap<HttpOutputMessage, ObservedBody>();
        private final Deque<ExchangeScope> scopes = new ArrayDeque<ExchangeScope>();
    }
}
