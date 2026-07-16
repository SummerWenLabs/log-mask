/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import io.github.summerwenlabs.log.mask.governance.MaskType;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategies;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;

/**
 * Writes a string property through a content masking strategy.
 *
 * <p>A non-string value, a custom strategy failure, or a custom strategy that
 * returns {@code null} is isolated to the current property and replaced with a
 * fixed redaction. Other properties remain serializable.
 *
 * @author SummerWen
 * @since 0.1
 */
final class ContentMaskingBeanPropertyWriter extends BeanPropertyWriter {

    private static final long serialVersionUID = 1L;
    private static final String REDACTION = "<redacted>";

    private final MaskTypeDefinition definition;
    private final boolean custom;
    private final MaskingDiagnostics diagnostics;

    ContentMaskingBeanPropertyWriter(
            BeanPropertyWriter source,
            MaskType type,
            MaskingDiagnostics diagnostics) {
        super(source);
        this.definition = MaskStrategies.builtIn(type);
        this.custom = false;
        this.diagnostics = diagnostics;
    }

    ContentMaskingBeanPropertyWriter(
            BeanPropertyWriter source,
            MaskTypeDefinition customDefinition,
            MaskingDiagnostics diagnostics) {
        super(source);
        this.definition = customDefinition;
        this.custom = true;
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
        if (!custom) {
            return definition.mask(value);
        }
        try {
            String result = definition.mask(value);
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

}
