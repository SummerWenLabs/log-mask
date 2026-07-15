package io.github.summerwenlabs.log.mask;

/**
 * Built-in property governance rules available in the current core API.
 */
public enum MaskType {
    UNSPECIFIED,
    REDACT,
    PHONE,
    EMAIL,
    ID_CARD,
    BANK_CARD,
    FULL,
    CUSTOM
}
