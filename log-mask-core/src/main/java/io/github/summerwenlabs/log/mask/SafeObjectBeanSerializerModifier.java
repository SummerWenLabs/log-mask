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
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;

/**
 * Replaces Jackson property writers with governance-aware log-only writers.
 *
 * <p>Rules are resolved from Jackson logical properties, so field, getter,
 * inheritance, and mix-in annotations follow the mapper's own introspection.
 * The modifier is installed only on the private mapper copy owned by
 * {@link LogMasker}.
 *
 * @author SummerWen
 * @since 0.1
 */
final class SafeObjectBeanSerializerModifier extends BeanSerializerModifier {

    private static final long serialVersionUID = 1L;

    private final MaskRuleResolver ruleResolver;
    private final MaskingDiagnostics diagnostics;

    SafeObjectBeanSerializerModifier(
            MaskStrategyRegistry strategyRegistry,
            MaskingDiagnostics diagnostics) {
        this.ruleResolver = new MaskRuleResolver(strategyRegistry);
        this.diagnostics = diagnostics;
    }

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
            } else if (definition != null) {
                applyMaskRule(
                        writers,
                        writer,
                        beanDescription.getBeanClass(),
                        ruleResolver.resolve(definition));
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

    private void applyMaskRule(
            ListIterator<BeanPropertyWriter> writers,
            BeanPropertyWriter writer,
            Class<?> beanType,
            MaskRule rule) {
        if (rule.action() == MaskRule.Action.REDACT) {
            if (rule.failureReason() != null) {
                diagnostics.warnOnce(beanType, writer.getName(), rule.failureReason());
            }
            writers.set(new RedactingBeanPropertyWriter(writer));
        } else if (rule.action() == MaskRule.Action.CONTENT && rule.builtInType() != null) {
            writers.set(
                    new ContentMaskingBeanPropertyWriter(
                            writer,
                            rule.builtInType(),
                            diagnostics));
        } else if (rule.action() == MaskRule.Action.CONTENT) {
            writers.set(
                    new ContentMaskingBeanPropertyWriter(
                            writer,
                            rule.definition(),
                            diagnostics));
        }
    }

    private boolean hasAnnotation(AnnotatedMember member, Class<LogExclude> annotationType) {
        return member != null && member.getAnnotation(annotationType) != null;
    }

}
