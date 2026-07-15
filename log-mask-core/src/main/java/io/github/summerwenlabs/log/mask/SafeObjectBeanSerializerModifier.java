package io.github.summerwenlabs.log.mask;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

final class SafeObjectBeanSerializerModifier extends BeanSerializerModifier {

    private static final long serialVersionUID = 1L;

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDescription,
            List<BeanPropertyWriter> beanProperties) {
        Map<String, BeanPropertyDefinition> definitions = definitionsByName(beanDescription);
        ListIterator<BeanPropertyWriter> writers = beanProperties.listIterator();
        while (writers.hasNext()) {
            BeanPropertyWriter writer = writers.next();
            BeanPropertyDefinition definition = definitions.get(writer.getName());
            if (definition != null && isExcluded(definition)) {
                writers.set(new ExcludingBeanPropertyWriter(writer));
            } else if (definition != null && isRedacted(definition)) {
                writers.set(new RedactingBeanPropertyWriter(writer));
            }
        }
        return beanProperties;
    }

    private Map<String, BeanPropertyDefinition> definitionsByName(BeanDescription beanDescription) {
        Map<String, BeanPropertyDefinition> definitions = new HashMap<String, BeanPropertyDefinition>();
        for (BeanPropertyDefinition definition : beanDescription.findProperties()) {
            definitions.put(definition.getName(), definition);
        }
        return definitions;
    }

    private boolean isExcluded(BeanPropertyDefinition definition) {
        return hasAnnotation(definition.getField(), LogExclude.class)
                || hasAnnotation(definition.getGetter(), LogExclude.class);
    }

    private boolean isRedacted(BeanPropertyDefinition definition) {
        Mask fieldMask = annotation(definition.getField(), Mask.class);
        Mask getterMask = annotation(definition.getGetter(), Mask.class);
        return isRedact(fieldMask) || isRedact(getterMask);
    }

    private boolean isRedact(Mask mask) {
        return mask != null && mask.type() == MaskType.REDACT;
    }

    private boolean hasAnnotation(AnnotatedMember member, Class<LogExclude> annotationType) {
        return member != null && member.getAnnotation(annotationType) != null;
    }

    private <A extends java.lang.annotation.Annotation> A annotation(
            AnnotatedMember member,
            Class<A> annotationType) {
        return member == null ? null : member.getAnnotation(annotationType);
    }
}
