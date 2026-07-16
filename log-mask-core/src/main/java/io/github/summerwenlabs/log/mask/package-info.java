/* SPDX-License-Identifier: Apache-2.0 */

/**
 * Generates safe, log-only JSON representations without changing application
 * serialization.
 *
 * <p>Use {@link LogMasker} as the main entry point. Bounded generation returns
 * a complete {@link BoundedMaskResult}, while generation failures surface as
 * {@link LogMaskException}. Package-private implementations preserve
 * field-level safety and UTF-8 budget guarantees.
 *
 * @since 0.1
 */
package io.github.summerwenlabs.log.mask;
