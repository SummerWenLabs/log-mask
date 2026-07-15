package io.github.summerwenlabs.log.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

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
