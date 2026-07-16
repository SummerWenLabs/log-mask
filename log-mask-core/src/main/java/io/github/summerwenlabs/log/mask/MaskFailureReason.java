/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask;

/**
 * Identifies a property-level governance failure for deduplicated diagnostics.
 *
 * @author SummerWen
 * @since 0.1
 */
enum MaskFailureReason {
    CONFLICTING_DECLARATIONS("conflicting_declarations"),
    INVALID_MODE("invalid_mode"),
    UNKNOWN_TYPE_CODE("unknown_type_code"),
    INVALID_PATTERN("invalid_pattern"),
    NON_STRING_VALUE("non_string_value"),
    STRATEGY_RETURNED_NULL("strategy_returned_null"),
    STRATEGY_FAILED("strategy_failed");

    private final String code;

    MaskFailureReason(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
