/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.exchange;

import io.github.summerwenlabs.log.mask.http.NameValueCollection;
import io.github.summerwenlabs.log.mask.http.RegionState;

/**
 * Holds immutable response facts for one terminal HTTP exchange event.
 *
 * <p>Headers and body are each paired with an execution state. Absence of an
 * entire response is represented by {@code null} on {@link HttpExchangeEvent},
 * never by an instance of this type.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpExchangeResponse {

    private final int status;
    private final RegionValue<NameValueCollection> headers;
    private final RegionValue<JsonValue> body;

    private HttpExchangeResponse(Builder builder) {
        if (builder.status == null) {
            throw new IllegalStateException("status must be configured");
        }
        this.status = builder.status;
        this.headers = requireRegion(builder.headers, "headers");
        this.body = requireRegion(builder.body, "body");
    }

    /**
     * Create a builder for response facts.
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the numeric HTTP response status.
     * @return the response status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Return the header execution state paired with {@link #getHeaders()}.
     * @return the header state
     */
    public RegionState getHeadersState() {
        return headers.state();
    }

    /**
     * Return the immutable response headers.
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
     * Return the JSON response body representation.
     * @return a JSON value; JSON {@code null} means actual body absence
     */
    public JsonValue getBody() {
        return body.value();
    }

    private static <T> RegionValue<T> requireRegion(RegionValue<T> value, String name) {
        if (value == null) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    /**
     * Builds a response with status and both governed regions.
     *
     * <p>Status, headers state/value, and body state/value are required. Region
     * methods validate state/value pairings immediately.
     */
    public static final class Builder {
        private Integer status;
        private RegionValue<NameValueCollection> headers;
        private RegionValue<JsonValue> body;

        private Builder() {
        }

        /**
         * Set the numeric HTTP response status.
         * @param status response status code
         * @return this builder
         */
        public Builder status(int status) {
            this.status = status;
            return this;
        }

        /**
         * Set the response header state/value pair.
         * @param state final header execution state
         * @param value headers, or {@code null} only when disabled
         * @return this builder
         */
        public Builder headers(RegionState state, NameValueCollection value) {
            this.headers = RegionValue.headers(state, value);
            return this;
        }

        /**
         * Set the response body state/value pair.
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
         * Build the immutable response and validate required facts.
         * @return a new response representation
         * @throws IllegalStateException if status or a region was not
         * configured
         */
        public HttpExchangeResponse build() {
            return new HttpExchangeResponse(this);
        }
    }
}
