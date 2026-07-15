package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import io.github.summerwenlabs.log.mask.http.JsonValue;
import io.github.summerwenlabs.log.mask.http.RegionState;

final class ObservedBody {

    private static final ObservedBody ABSENT =
            new ObservedBody(RegionState.SUCCESS, JsonValue.nullValue());
    private static final ObservedBody LIMIT_EXCEEDED =
            new ObservedBody(RegionState.LIMIT_EXCEEDED, JsonValue.emptyString());
    private static final ObservedBody PROCESSING_FAILED =
            new ObservedBody(RegionState.PROCESSING_FAILED, JsonValue.emptyString());

    private final RegionState state;
    private final JsonValue value;

    private ObservedBody(RegionState state, JsonValue value) {
        this.state = state;
        this.value = value;
    }

    static ObservedBody absent() {
        return ABSENT;
    }

    static ObservedBody success(String json) {
        return new ObservedBody(RegionState.SUCCESS, JsonValue.ofJson(json));
    }

    static ObservedBody limitExceeded() {
        return LIMIT_EXCEEDED;
    }

    static ObservedBody processingFailed() {
        return PROCESSING_FAILED;
    }

    RegionState state() {
        return state;
    }

    JsonValue value() {
        return value;
    }
}
