/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.exchange;

/**
 * JSON shape used for ordered HTTP name/value collections.
 *
 * @author SummerWen
 * @since 0.1
 */
public enum NameValueShape {
    /** Array of objects with explicit {@code name} and {@code values} fields. */
    STANDARD,
    /** Object whose property names map directly to arrays of values. */
    COMPACT
}
