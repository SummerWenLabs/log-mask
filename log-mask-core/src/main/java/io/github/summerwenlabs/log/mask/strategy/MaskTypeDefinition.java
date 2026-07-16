/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.strategy;

/**
 * Defines one string content masking strategy.
 *
 * <p>Application-defined instances are registered under an exact custom code.
 * Implementations may be called concurrently and should be thread-safe.
 *
 * @author SummerWen
 * @since 0.1
 */
public interface MaskTypeDefinition {

    /**
     * Return the exact custom strategy code.
     * @return a {@code non-empty} code with no surrounding whitespace
     */
    String getTypeCode();

    /**
     * Generate the safe replacement for one {@code non-null} string value.
     * @param value original property value; never {@code null}
     * @return the safe replacement; {@code null} triggers fixed redaction
     */
    String mask(String value);
}
