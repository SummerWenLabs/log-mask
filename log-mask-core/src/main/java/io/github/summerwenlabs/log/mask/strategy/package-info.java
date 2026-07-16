/* SPDX-License-Identifier: Apache-2.0 */

/**
 * Defines and registers built-in and application-defined masking strategies.
 *
 * <p>Implement {@link MaskTypeDefinition} for a thread-safe custom strategy,
 * collect definitions in an immutable {@link MaskStrategyRegistry}, and use
 * {@link MaskStrategies} for built-in content masking. Custom codes are exact
 * and are independent of the built-in type namespace.
 *
 * @since 0.1
 */
package io.github.summerwenlabs.log.mask.strategy;
