package io.github.summerwenlabs.log.mask.http;

import java.util.Objects;

/**
 * Holds immutable request facts for one terminal HTTP exchange event.
 *
 * <p>URI, headers, and body are each paired with an execution state. A disabled
 * headers region may be {@code null}; body regions always carry a JSON value,
 * using an empty JSON string for disabled or failed output.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpExchangeRequest {

    private final String method;
    private final HttpRequestUri uri;
    private final RegionValue<NameValueCollection> headers;
    private final RegionValue<JsonValue> body;

    private HttpExchangeRequest(Builder builder) {
        this.method = requireText(builder.method, "method");
        this.uri = requireValue(builder.uri, "uri");
        this.headers = requireRegion(builder.headers, "headers");
        this.body = requireRegion(builder.body, "body");
    }

    /**
     * Create a builder for request facts.
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the original HTTP method token.
     * @return a non-blank method
     */
    public String getMethod() {
        return method;
    }

    /**
     * Return the URI execution state paired with {@link #getUri()}.
     * @return the URI state
     */
    public RegionState getUriState() {
        return uri.getState();
    }

    /**
     * Return the immutable governed URI.
     * @return the URI representation
     */
    public HttpRequestUri getUri() {
        return uri;
    }

    /**
     * Return the header execution state paired with {@link #getHeaders()}.
     * @return the header state
     */
    public RegionState getHeadersState() {
        return headers.state();
    }

    /**
     * Return the immutable request headers.
     * @return headers, or {@code null} when the region is disabled
     */
    public NameValueCollection getHeaders() {
        return headers.value();
    }

    /**
     * Return the body execution state paired with {@link #getBody()}.
     * @return the body state
     */
    public RegionState getBodyState() {
        return body.state();
    }

    /**
     * Return the JSON request body representation.
     * @return a JSON value; JSON {@code null} means actual body absence
     */
    public JsonValue getBody() {
        return body.value();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <T> T requireValue(T value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    private static <T> RegionValue<T> requireRegion(RegionValue<T> value, String name) {
        return requireValue(value, name);
    }

    /**
     * Builds a request with a method and all three governed regions.
     *
     * <p>Method, URI, headers state/value, and body state/value are required.
     * Region methods validate state/value pairings immediately.
     */
    public static final class Builder {
        private String method;
        private HttpRequestUri uri;
        private RegionValue<NameValueCollection> headers;
        private RegionValue<JsonValue> body;

        private Builder() {
        }

        /**
         * Set the HTTP method token.
         * @param method non-blank method
         * @return this builder
         */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /**
         * Set the required governed URI.
         * @param uri immutable URI representation
         * @return this builder
         * @throws NullPointerException if {@code uri} is {@code null}
         */
        public Builder uri(HttpRequestUri uri) {
            this.uri = Objects.requireNonNull(uri, "uri");
            return this;
        }

        /**
         * Set the request header state/value pair.
         * @param state final header execution state
         * @param value headers, or {@code null} only when disabled
         * @return this builder
         */
        public Builder headers(RegionState state, NameValueCollection value) {
            this.headers = RegionValue.headers(state, value);
            return this;
        }

        /**
         * Set the request body state/value pair.
         * @param state final body execution state
     * @param value JSON value; failed or disabled states require an empty
     * string
         * @return this builder
         */
        public Builder body(RegionState state, JsonValue value) {
            this.body = RegionValue.body(state, value);
            return this;
        }

        /**
         * Build the immutable request and validate required region facts.
         * @return a new request representation
         * @throws IllegalArgumentException if the method is blank
         * @throws IllegalStateException if a required region was not configured
         */
        public HttpExchangeRequest build() {
            return new HttpExchangeRequest(this);
        }
    }
}
