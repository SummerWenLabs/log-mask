package io.github.summerwenlabs.log.mask.http;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable facts for one terminal HTTP exchange event.
 */
public final class HttpExchangeEvent {

    public static final String EVENT_NAME = "http_exchange";
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

    public static Builder builder() {
        return new Builder();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public UUID getExchangeId() {
        return exchangeId;
    }

    public String getTraceId() {
        return traceId;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isGovernanceEnabled() {
        return governanceEnabled;
    }

    public HttpExchangeRequest getRequest() {
        return request;
    }

    public HttpExchangeResponse getResponse() {
        return response;
    }

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

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder exchangeId(UUID exchangeId) {
            this.exchangeId = exchangeId;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder governanceEnabled(boolean governanceEnabled) {
            this.governanceEnabled = governanceEnabled;
            return this;
        }

        public Builder request(HttpExchangeRequest request) {
            this.request = request;
            return this;
        }

        public Builder response(HttpExchangeResponse response) {
            this.response = response;
            return this;
        }

        public HttpExchangeEvent build() {
            return new HttpExchangeEvent(this);
        }
    }
}
