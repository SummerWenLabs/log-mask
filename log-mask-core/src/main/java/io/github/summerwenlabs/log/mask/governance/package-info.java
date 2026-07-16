/* SPDX-License-Identifier: Apache-2.0 */

/**
 * Declares field-level governance for log-only JSON representations.
 *
 * <p>Use {@link Mask} to select a built-in, custom-code, or inline pattern
 * rule from {@link MaskType}, and use {@link LogExclude} to omit a property
 * without reading it. These annotations do not change application
 * serialization.
 *
 * @since 0.1
 */
package io.github.summerwenlabs.log.mask.governance;
