package io.github.summerwenlabs.log.mask;

import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

final class MaskRuleResolver {

    private final MaskStrategyRegistry strategyRegistry;

    MaskRuleResolver(MaskStrategyRegistry strategyRegistry) {
        this.strategyRegistry = strategyRegistry;
    }

    MaskRule resolve(BeanPropertyDefinition property) {
        Mask fieldMask = annotation(property.getField(), Mask.class);
        Mask getterMask = annotation(property.getGetter(), Mask.class);
        if (fieldMask != null && getterMask != null && !fieldMask.equals(getterMask)) {
            return MaskRule.redact(MaskFailureReason.CONFLICTING_DECLARATIONS);
        }
        Mask mask = fieldMask != null ? fieldMask : getterMask;
        if (mask == null) {
            return MaskRule.none();
        }
        return resolve(mask);
    }

    private MaskRule resolve(Mask mask) {
        if (mask.type() == MaskType.UNSPECIFIED) {
            return resolveCustomCode(mask);
        }
        if (mask.type() == MaskType.CUSTOM) {
            return resolveInlinePattern(mask);
        }
        if (!mask.typeCode().isEmpty()
                || !mask.pattern().isEmpty()
                || !mask.replacement().isEmpty()) {
            return MaskRule.redact(MaskFailureReason.INVALID_MODE);
        }
        if (mask.type() == MaskType.REDACT) {
            return MaskRule.redact();
        }
        return MaskRule.content(mask.type());
    }

    private MaskRule resolveCustomCode(Mask mask) {
        if (!MaskTypeCode.isValid(mask.typeCode())
                || !mask.pattern().isEmpty()
                || !mask.replacement().isEmpty()) {
            return MaskRule.redact(MaskFailureReason.INVALID_MODE);
        }
        MaskTypeDefinition definition = strategyRegistry.find(mask.typeCode()).orElse(null);
        return definition == null
                ? MaskRule.redact(MaskFailureReason.UNKNOWN_TYPE_CODE)
                : MaskRule.content(definition);
    }

    private MaskRule resolveInlinePattern(Mask mask) {
        if (!mask.typeCode().isEmpty() || mask.pattern().isEmpty()) {
            return MaskRule.redact(MaskFailureReason.INVALID_MODE);
        }
        try {
            return MaskRule.content(
                    new RegexMaskTypeDefinition(mask.pattern(), mask.replacement()));
        } catch (PatternSyntaxException exception) {
            return MaskRule.redact(MaskFailureReason.INVALID_PATTERN);
        }
    }

    private <A extends java.lang.annotation.Annotation> A annotation(
            AnnotatedMember member,
            Class<A> annotationType) {
        return member == null ? null : member.getAnnotation(annotationType);
    }
}
