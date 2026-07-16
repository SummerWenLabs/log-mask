package io.github.summerwenlabs.log.mask.strategy;

import java.util.Objects;

/**
 * Validates and preserves an exact application-defined strategy code.
 *
 * @author SummerWen
 * @since 0.1
 */
final class MaskTypeCode {

    private final String value;

    private MaskTypeCode(String value) {
        this.value = value;
    }

    static MaskTypeCode of(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException(
                    "Custom mask type code must be non-empty and have no surrounding whitespace");
        }
        return new MaskTypeCode(value);
    }

    static boolean isValid(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        int first = value.codePointAt(0);
        int last = value.codePointBefore(value.length());
        return !isWhitespace(first) && !isWhitespace(last);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaskTypeCode)) {
            return false;
        }
        MaskTypeCode that = (MaskTypeCode) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
