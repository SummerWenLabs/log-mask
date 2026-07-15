package io.github.summerwenlabs.log.mask;

/**
 * Signals that a safe object representation could not be generated.
 */
public final class LogMaskException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LogMaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
