/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import io.github.summerwenlabs.log.mask.http.RegionState;
import io.github.summerwenlabs.log.mask.http.exchange.JsonValue;

/**
 * Pairs one body log value with its final execution state.
 *
 * <p>Actual absence is JSON {@code null}. Disabled, over-budget, and failed
 * output use an empty JSON string so they remain distinguishable from absence.
 *
 * @author SummerWen
 * @since 0.1
 */
final class ObservedBody {

    private static final ObservedBody ABSENT =
            new ObservedBody(RegionState.SUCCESS, JsonValue.nullValue());
    private static final ObservedBody LIMIT_EXCEEDED =
            new ObservedBody(RegionState.LIMIT_EXCEEDED, JsonValue.emptyString());
    private static final ObservedBody PROCESSING_FAILED =
            new ObservedBody(RegionState.PROCESSING_FAILED, JsonValue.emptyString());
    private static final ObservedBody DISABLED =
            new ObservedBody(RegionState.DISABLED, JsonValue.emptyString());

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

    static ObservedBody disabled() {
        return DISABLED;
    }

    RegionState state() {
        return state;
    }

    JsonValue value() {
        return value;
    }
}
