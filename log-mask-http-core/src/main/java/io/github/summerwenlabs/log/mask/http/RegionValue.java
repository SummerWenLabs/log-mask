package io.github.summerwenlabs.log.mask.http;

import java.util.Objects;

final class RegionValue {

    private final RegionState state;
    private final JsonValue value;

    private RegionValue(RegionState state, JsonValue value) {
        this.state = state;
        this.value = value;
    }

    static RegionValue uri(RegionState state, JsonValue value) {
        return create(RegionKind.URI, state, value);
    }

    static RegionValue headers(RegionState state, JsonValue value) {
        return create(RegionKind.HEADERS, state, value);
    }

    static RegionValue body(RegionState state, JsonValue value) {
        return create(RegionKind.BODY, state, value);
    }

    private static RegionValue create(RegionKind region, RegionState state, JsonValue value) {
        Objects.requireNonNull(state, region.label + " state");
        Objects.requireNonNull(value, region.label + " value");
        if (region != RegionKind.BODY && state == RegionState.LIMIT_EXCEEDED) {
            throw new IllegalArgumentException("LIMIT_EXCEEDED is only valid for body regions");
        }
        if (region == RegionKind.BODY && requiresEmptyBody(state) && !value.isEmptyString()) {
            throw new IllegalArgumentException(state + " body must be an empty JSON string");
        }
        if (region == RegionKind.HEADERS && state == RegionState.DISABLED && !value.isNull()) {
            throw new IllegalArgumentException("DISABLED headers must be JSON null");
        }
        return new RegionValue(state, value);
    }

    private static boolean requiresEmptyBody(RegionState state) {
        return state == RegionState.LIMIT_EXCEEDED
                || state == RegionState.PROCESSING_FAILED
                || state == RegionState.DISABLED;
    }

    RegionState state() {
        return state;
    }

    JsonValue value() {
        return value;
    }

    private enum RegionKind {
        URI("uri"),
        HEADERS("headers"),
        BODY("body");

        private final String label;

        RegionKind(String label) {
            this.label = label;
        }
    }
}
