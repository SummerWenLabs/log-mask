/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http;

/**
 * Final result of producing one governed region of an HTTP exchange event.
 *
 * <p>The state evaluates execution of explicit rules and output generation. It
 * does not claim that unchanged values are insensitive.
 *
 * @author SummerWen
 * @since 0.1
 */
public enum RegionState {
    /** Region output was produced without a governance fallback. */
    SUCCESS,
    /** At least one explicit rule used fixed redaction as a safe fallback. */
    FALLBACK_APPLIED,
    /** Body output exceeded its UTF-8 byte budget. */
    LIMIT_EXCEEDED,
    /** Region output could not be produced as configured. */
    PROCESSING_FAILED,
    /** Region observation was explicitly disabled. */
    DISABLED
}
