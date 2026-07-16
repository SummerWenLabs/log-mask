/* SPDX-License-Identifier: Apache-2.0 */

/**
 * Spring Boot 2 auto-configuration for RestTemplate HTTP exchange
 * observation.
 *
 * <p>The package binds adapter settings and installs the observation runtime
 * without replacing application transport, converters, or error handling.
 * Applications normally select RestTemplate beans through the public
 * contracts in {@code io.github.summerwenlabs.log.mask.resttemplate.boot2}.
 *
 * @since 0.1
 */
package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;
