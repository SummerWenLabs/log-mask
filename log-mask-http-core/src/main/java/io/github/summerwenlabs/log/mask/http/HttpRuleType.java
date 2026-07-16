package io.github.summerwenlabs.log.mask.http;

/**
 * Built-in governance actions available to HTTP value rules.
 *
 * @author SummerWen
 * @since 0.1
 */
public enum HttpRuleType {
    /** Replace a value with fixed redaction without observing its contents. */
    REDACT,
    /** Remove the complete name and all of its values from the log region. */
    EXCLUDE,
    /** Apply the built-in phone content strategy. */
    PHONE,
    /** Apply the built-in email content strategy. */
    EMAIL,
    /** Apply the built-in identity-card content strategy. */
    ID_CARD,
    /** Apply the built-in bank-card content strategy. */
    BANK_CARD,
    /** Replace each Unicode code point with one asterisk. */
    FULL
}
