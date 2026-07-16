/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.exchange;

/**
 * Signals that an HTTP exchange event could not be written as JSON.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpExchangeWriteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create an exception for a failed event rendering operation.
     * @param message description of the failed operation
     * @param cause underlying JSON writing failure
     */
    public HttpExchangeWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
