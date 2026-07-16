/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.exchange;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Holds immutable facts for one terminal HTTP exchange event.
 *
 * <p>A request is always present. A {@code null} response represents an
 * exchange that did not obtain an HTTP response; it is not an error
 * classification. The exchange identifier must be UUID version 4.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpExchangeEvent {

    /** Stable event discriminator written into every exchange event. */
    public static final String EVENT_NAME = "http_exchange";

    /** Current fixed JSON schema version. */
    public static final int SCHEMA_VERSION = 1;

    private final Instant timestamp;
    private final UUID exchangeId;
    private final String traceId;
    private final long durationMs;
    private final boolean governanceEnabled;
    private final HttpExchangeRequest request;
    private final HttpExchangeResponse response;

    private HttpExchangeEvent(Builder builder) {
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp");
        this.exchangeId = Objects.requireNonNull(builder.exchangeId, "exchangeId");
        if (exchangeId.version() != 4) {
            throw new IllegalArgumentException("exchangeId must be a UUID v4");
        }
        if (builder.durationMs == null || builder.durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be non-negative");
        }
        if (builder.governanceEnabled == null) {
            throw new IllegalStateException("governanceEnabled must be configured");
        }
        this.traceId = builder.traceId;
        this.durationMs = builder.durationMs;
        this.governanceEnabled = builder.governanceEnabled;
        this.request = Objects.requireNonNull(builder.request, "request");
        this.response = builder.response;
    }

    /**
     * Create a builder for one terminal exchange event.
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the terminal event timestamp.
     * @return the {@code non-null} timestamp supplied to the builder
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Return the unique UUID version 4 exchange identifier.
     * @return the {@code non-null} exchange identifier
     */
    public UUID getExchangeId() {
        return exchangeId;
    }

    /**
     * Return the host-provided trace identifier.
     * @return the trace ID, or {@code null} when none was supplied
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Return downstream exchange duration in integer milliseconds.
     * @return a non-negative duration
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Determine whether explicit governance was enabled for this event.
     * @return {@code true} when governance rules were active
     */
    public boolean isGovernanceEnabled() {
        return governanceEnabled;
    }

    /**
     * Return the immutable request facts.
     * @return the {@code non-null} request
     */
    public HttpExchangeRequest getRequest() {
        return request;
    }

    /**
     * Return the immutable response facts when a response was obtained.
     * @return the response, or {@code null} for a request-only exchange
     */
    public HttpExchangeResponse getResponse() {
        return response;
    }

    /**
     * Builds an event after all terminal facts are known.
     *
     * <p>Timestamp, UUID v4 exchange ID, non-negative duration, governance
     * flag, and request are required. Trace ID and response may be
     * {@code null}.
     */
    public static final class Builder {
        private Instant timestamp;
        private UUID exchangeId;
        private String traceId;
        private Long durationMs;
        private Boolean governanceEnabled;
        private HttpExchangeRequest request;
        private HttpExchangeResponse response;

        private Builder() {
        }

        /**
         * Set the terminal timestamp.
         * @param timestamp timestamp to retain
         * @return this builder
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Set the UUID version 4 exchange identifier.
         * @param exchangeId unique exchange identifier
         * @return this builder
         */
        public Builder exchangeId(UUID exchangeId) {
            this.exchangeId = exchangeId;
            return this;
        }

        /**
         * Set the host-provided trace identifier.
         * @param traceId trace ID, or {@code null}
         * @return this builder
         */
        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        /**
         * Set the downstream duration in integer milliseconds.
         * @param durationMs non-negative duration
         * @return this builder
         */
        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        /**
         * Set whether explicit governance was enabled.
         * @param governanceEnabled governance execution flag
         * @return this builder
         */
        public Builder governanceEnabled(boolean governanceEnabled) {
            this.governanceEnabled = governanceEnabled;
            return this;
        }

        /**
         * Set the required request facts.
         * @param request immutable request representation
         * @return this builder
         */
        public Builder request(HttpExchangeRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Set response facts when an HTTP response was obtained.
         * @param response response representation, or {@code null}
         * @return this builder
         */
        public Builder response(HttpExchangeResponse response) {
            this.response = response;
            return this;
        }

        /**
         * Build the immutable event and validate all required facts.
         * @return a new terminal event
         * @throws NullPointerException if timestamp, exchange ID, or request is
         * {@code null}
         * @throws IllegalArgumentException if the ID is not UUID v4 or duration
         * is negative
     * @throws IllegalArgumentException if duration is negative
     * @throws IllegalStateException if governance was not configured
         */
        public HttpExchangeEvent build() {
            return new HttpExchangeEvent(this);
        }
    }
}
