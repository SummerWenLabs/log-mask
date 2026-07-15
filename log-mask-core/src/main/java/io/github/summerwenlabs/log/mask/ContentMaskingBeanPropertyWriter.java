package io.github.summerwenlabs.log.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

final class ContentMaskingBeanPropertyWriter extends BeanPropertyWriter {

    private static final long serialVersionUID = 1L;
    private static final String REDACTION = "<redacted>";

    private final MaskType type;
    private final MaskTypeDefinition customDefinition;
    private final MaskingDiagnostics diagnostics;

    ContentMaskingBeanPropertyWriter(
            BeanPropertyWriter source,
            MaskType type,
            MaskingDiagnostics diagnostics) {
        super(source);
        this.type = type;
        this.customDefinition = null;
        this.diagnostics = diagnostics;
    }

    ContentMaskingBeanPropertyWriter(
            BeanPropertyWriter source,
            MaskTypeDefinition customDefinition,
            MaskingDiagnostics diagnostics) {
        super(source);
        this.type = null;
        this.customDefinition = customDefinition;
        this.diagnostics = diagnostics;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator generator, SerializerProvider provider)
            throws Exception {
        generator.writeFieldName(getName());
        writeMaskedValue(bean, generator, provider);
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializerProvider provider)
            throws Exception {
        writeMaskedValue(bean, generator, provider);
    }

    private void writeMaskedValue(
            Object bean,
            JsonGenerator generator,
            SerializerProvider provider) throws Exception {
        Object value = get(bean);
        if (value == null) {
            provider.defaultSerializeNull(generator);
        } else if (!(value instanceof String)) {
            diagnostics.warnOnce(
                    bean.getClass(),
                    getName(),
                    MaskFailureReason.NON_STRING_VALUE);
            generator.writeString(REDACTION);
        } else {
            generator.writeString(maskOrRedact(bean, (String) value));
        }
    }

    private String maskOrRedact(Object bean, String value) {
        if (customDefinition == null) {
            return mask(value);
        }
        try {
            String result = customDefinition.mask(value);
            if (result == null) {
                diagnostics.warnOnce(
                        bean.getClass(),
                        getName(),
                        MaskFailureReason.STRATEGY_RETURNED_NULL);
                return REDACTION;
            }
            return result;
        } catch (RuntimeException exception) {
            diagnostics.warnOnce(
                    bean.getClass(),
                    getName(),
                    MaskFailureReason.STRATEGY_FAILED);
            return REDACTION;
        }
    }

    private String mask(String value) {
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
                break;
        }
        return stars(value.codePointCount(0, value.length()));
    }

    private String maskEmail(String value) {
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

    private boolean containsWhitespace(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private String stars(int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            result.append('*');
        }
        return result.toString();
    }
}
