package io.github.summerwenlabs.log.mask;

final class BuiltInMaskTypeDefinition implements MaskTypeDefinition {

    private final MaskType type;

    BuiltInMaskTypeDefinition(MaskType type) {
        this.type = type;
    }

    @Override
    public String getTypeCode() {
        return type.name();
    }

    @Override
    public String mask(String value) {
        switch (type) {
            case PHONE:
                if (value.matches("[0-9]{11}")) {
                    return value.substring(0, 3) + "****" + value.substring(7);
                }
                break;
            case EMAIL:
                String email = maskEmail(value);
                if (email != null) {
                    return email;
                }
                break;
            case ID_CARD:
                if (value.matches("[0-9]{17}[0-9Xx]")) {
                    return value.substring(0, 6) + "********" + value.substring(14);
                }
                break;
            case BANK_CARD:
                if (value.matches("[0-9]{12,19}")) {
                    return value.substring(0, 4)
                            + stars(value.length() - 8)
                            + value.substring(value.length() - 4);
                }
                break;
            case FULL:
                return stars(value.codePointCount(0, value.length()));
            default:
                throw new IllegalStateException("Unsupported built-in content type: " + type);
        }
        return stars(value.codePointCount(0, value.length()));
    }

    private static String maskEmail(String value) {
        int separator = value.indexOf('@');
        if (separator <= 0
                || separator != value.lastIndexOf('@')
                || separator == value.length() - 1
                || containsWhitespace(value)) {
            return null;
        }
        String localPart = value.substring(0, separator);
        int localLength = localPart.codePointCount(0, localPart.length());
        if (localLength < 2) {
            return null;
        }
        int firstEnd = localPart.offsetByCodePoints(0, 1);
        return localPart.substring(0, firstEnd)
                + stars(localLength - 1)
                + value.substring(separator);
    }

    private static boolean containsWhitespace(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static String stars(int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append('*');
        }
        return result.toString();
    }
}
