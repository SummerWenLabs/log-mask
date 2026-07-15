package io.github.summerwenlabs.log.mask.http;

/**
 * Request facts included in one terminal HTTP exchange event.
 */
public final class HttpExchangeRequest {

    private final String method;
    private final RegionValue uri;
    private final RegionValue headers;
    private final RegionValue body;

    private HttpExchangeRequest(Builder builder) {
        this.method = requireText(builder.method, "method");
        this.uri = requireRegion(builder.uri, "uri");
        this.headers = requireRegion(builder.headers, "headers");
        this.body = requireRegion(builder.body, "body");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getMethod() {
        return method;
    }

    public RegionState getUriState() {
        return uri.state();
    }

    public JsonValue getUri() {
        return uri.value();
    }

    public RegionState getHeadersState() {
        return headers.state();
    }

    public JsonValue getHeaders() {
        return headers.value();
    }

    public RegionState getBodyState() {
        return body.state();
    }

    public JsonValue getBody() {
        return body.value();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static RegionValue requireRegion(RegionValue value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    public static final class Builder {
        private String method;
        private RegionValue uri;
        private RegionValue headers;
        private RegionValue body;

        private Builder() {
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder uri(RegionState state, JsonValue value) {
            this.uri = RegionValue.uri(state, value);
            return this;
        }

        public Builder headers(RegionState state, JsonValue value) {
            this.headers = RegionValue.headers(state, value);
            return this;
        }

        public Builder body(RegionState state, JsonValue value) {
            this.body = RegionValue.body(state, value);
            return this;
        }

        public HttpExchangeRequest build() {
            return new HttpExchangeRequest(this);
        }
    }
}
