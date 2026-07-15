package io.github.summerwenlabs.log.mask.http;

import java.util.Objects;

/**
 * Request facts included in one terminal HTTP exchange event.
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

    public static Builder builder() {
        return new Builder();
    }

    public String getMethod() {
        return method;
    }

    public RegionState getUriState() {
        return uri.getState();
    }

    public HttpRequestUri getUri() {
        return uri;
    }

    public RegionState getHeadersState() {
        return headers.state();
    }

    public NameValueCollection getHeaders() {
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

    private static <T> T requireValue(T value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    private static <T> RegionValue<T> requireRegion(RegionValue<T> value, String name) {
        return requireValue(value, name);
    }

    public static final class Builder {
        private String method;
        private HttpRequestUri uri;
        private RegionValue<NameValueCollection> headers;
        private RegionValue<JsonValue> body;

        private Builder() {
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder uri(HttpRequestUri uri) {
            this.uri = Objects.requireNonNull(uri, "uri");
            return this;
        }

        public Builder headers(RegionState state, NameValueCollection value) {
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
