package io.github.summerwenlabs.log.mask.http;

/**
 * Response facts included in one terminal HTTP exchange event.
 */
public final class HttpExchangeResponse {

    private final int status;
    private final RegionValue headers;
    private final RegionValue body;

    private HttpExchangeResponse(Builder builder) {
        if (builder.status == null) {
            throw new IllegalStateException("status must be configured");
        }
        this.status = builder.status;
        this.headers = requireRegion(builder.headers, "headers");
        this.body = requireRegion(builder.body, "body");
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getStatus() {
        return status;
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

    private static RegionValue requireRegion(RegionValue value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    public static final class Builder {
        private Integer status;
        private RegionValue headers;
        private RegionValue body;

        private Builder() {
        }

        public Builder status(int status) {
            this.status = status;
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

        public HttpExchangeResponse build() {
            return new HttpExchangeResponse(this);
        }
    }
}
