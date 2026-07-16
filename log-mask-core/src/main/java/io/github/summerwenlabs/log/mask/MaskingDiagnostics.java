/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Emits each governance fallback warning once per property and failure reason.
 *
 * <p>The concurrent key set bounds repeated log noise across calls while
 * preserving enough context to locate the affected model property.
 *
 * @author SummerWen
 * @since 0.1
 */
final class MaskingDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaskingDiagnostics.class);

    private final Set<String> reported = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());

    void warnOnce(Class<?> beanType, String propertyName, MaskFailureReason reason) {
        String key = beanType.getName() + '\u0000' + propertyName + '\u0000' + reason.code();
        if (!reported.add(key)) {
            return;
        }
        try {
            LOGGER.warn(
                    "Mask rule fallback: type={}, property={}, reason={}",
                    beanType.getName(),
                    propertyName,
                    reason.code());
        } catch (RuntimeException ignored) {
            // Diagnostics must never change safe rendering behavior.
        }
    }
}
