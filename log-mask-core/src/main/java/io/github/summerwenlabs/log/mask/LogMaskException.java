/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask;

/**
 * Signals that a safe object representation could not be generated.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class LogMaskException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create an exception for a failed safe representation operation.
     * @param message description of the failed operation
     * @param cause underlying Jackson or governance failure
     */
    public LogMaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
