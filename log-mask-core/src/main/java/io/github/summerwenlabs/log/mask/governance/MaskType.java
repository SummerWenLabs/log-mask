package io.github.summerwenlabs.log.mask.governance;

/**
 * Built-in property governance rules available in the current core API.
 *
 * @author SummerWen
 * @since 0.1
 */
public enum MaskType {
    /** Select a custom strategy by {@link Mask#typeCode()}. */
    UNSPECIFIED,
    /** Replace the value with a fixed redaction without reading it. */
    REDACT,
    /** Preserve the first three and last four digits of an 11-digit phone. */
    PHONE,
    /** Preserve the first local-part code point and the email domain. */
    EMAIL,
    /** Preserve the first six and last four characters of a valid ID card. */
    ID_CARD,
    /** Preserve the first and last four digits of a valid bank card. */
    BANK_CARD,
    /** Replace every Unicode code point with one asterisk. */
    FULL,
    /** Apply the inline regular expression declared by {@link Mask#pattern()}. */
    CUSTOM
}
