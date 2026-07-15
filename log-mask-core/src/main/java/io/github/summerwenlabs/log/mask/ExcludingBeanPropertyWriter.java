package io.github.summerwenlabs.log.mask;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

final class ExcludingBeanPropertyWriter extends BeanPropertyWriter {

    private static final long serialVersionUID = 1L;

    ExcludingBeanPropertyWriter(BeanPropertyWriter source) {
        super(source);
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator generator, SerializerProvider provider) {
        // Intentionally omit both the field name and its value without reading the bean.
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializerProvider provider) {
        // Array-shaped beans omit the element entirely without reading the bean.
    }
}
