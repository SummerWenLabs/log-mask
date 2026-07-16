package io.github.summerwenlabs.log.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

/**
 * Writes a fixed redaction for a property without reading its value.
 *
 * <p>This writer is used both for explicit complete redaction and as the local
 * fallback for invalid or conflicting governance declarations.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RedactingBeanPropertyWriter extends BeanPropertyWriter {

    private static final long serialVersionUID = 1L;
    private static final String REDACTION = "<redacted>";

    RedactingBeanPropertyWriter(BeanPropertyWriter source) {
        super(source);
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator generator, SerializerProvider provider)
            throws Exception {
        generator.writeFieldName(getName());
        generator.writeString(REDACTION);
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializerProvider provider)
            throws Exception {
        generator.writeString(REDACTION);
    }
}
