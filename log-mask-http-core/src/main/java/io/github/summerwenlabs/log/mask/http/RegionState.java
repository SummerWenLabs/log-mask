package io.github.summerwenlabs.log.mask.http;

/**
 * Final result of producing one governed region of an HTTP exchange event.
 */
public enum RegionState {
    SUCCESS,
    FALLBACK_APPLIED,
    LIMIT_EXCEEDED,
    PROCESSING_FAILED,
    DISABLED
}
