package io.github.summerwenlabs.log.mask.http;

/**
 * Built-in governance actions available to HTTP value rules.
 */
public enum HttpRuleType {
    REDACT,
    EXCLUDE,
    PHONE,
    EMAIL,
    ID_CARD,
    BANK_CARD,
    FULL
}
