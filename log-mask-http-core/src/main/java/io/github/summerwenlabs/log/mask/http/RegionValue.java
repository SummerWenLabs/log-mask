package io.github.summerwenlabs.log.mask.http;

import java.util.Objects;

/**
 * Validates the required pairing between one region state and its value.
 *
 * <p>Disabled headers use {@code null}. Body regions always use a JSON value;
 * disabled, failed, and over-budget bodies use an empty JSON string so
 * {@code null} remains reserved for an actually absent body.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RegionValue<T> {

    private final RegionState state;
    private final T value;

    private RegionValue(RegionState state, T value) {
        this.state = state;
        this.value = value;
    }

    static RegionValue<NameValueCollection> headers(
            RegionState state,
            NameValueCollection value) {
        Objects.requireNonNull(state, "headers state");
        if (state == RegionState.LIMIT_EXCEEDED) {
            throw new IllegalArgumentException("LIMIT_EXCEEDED is only valid for body regions");
        }
        if (state == RegionState.DISABLED && value != null) {
            throw new IllegalArgumentException("DISABLED headers must be null");
        }
        if (state != RegionState.DISABLED) {
            Objects.requireNonNull(value, "headers value");
        }
        return new RegionValue<NameValueCollection>(state, value);
    }

    static RegionValue<JsonValue> body(RegionState state, JsonValue value) {
        Objects.requireNonNull(state, "body state");
        Objects.requireNonNull(value, "body value");
        if (requiresEmptyBody(state) && !value.isEmptyString()) {
            throw new IllegalArgumentException(state + " body must be an empty JSON string");
        }
        return new RegionValue<JsonValue>(state, value);
    }

    private static boolean requiresEmptyBody(RegionState state) {
        return state == RegionState.LIMIT_EXCEEDED
                || state == RegionState.PROCESSING_FAILED
                || state == RegionState.DISABLED;
    }

    RegionState state() {
        return state;
    }

    T value() {
        return value;
    }
}
