package io.github.summerwenlabs.log.mask.http;

/**
 * Signals that an HTTP exchange event could not be written as JSON.
 */
public final class HttpExchangeWriteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HttpExchangeWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
