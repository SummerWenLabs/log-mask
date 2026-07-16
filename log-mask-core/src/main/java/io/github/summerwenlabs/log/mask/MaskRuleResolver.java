/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask;

import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;

/**
 * Resolves field and getter annotations into one deterministic property rule.
 *
 * <p>Conflicts, unknown codes, invalid modes, and invalid patterns resolve to
 * redaction. This fail-closed decision is local to the property and does not
 * prevent the remaining safe object representation from being generated.
 *
 * @author SummerWen
 * @since 0.1
 */
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
        if (!isValidTypeCode(mask.typeCode())
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

    private static boolean isValidTypeCode(String value) {
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
}
