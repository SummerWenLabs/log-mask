package io.github.summerwenlabs.log.mask.governance;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies an explicit governance rule to a Jackson logical property.
 *
 * <p>Declare exactly one mode: a built-in {@link #type()}, an exact custom
 * {@link #typeCode()}, or an inline regular expression using
 * {@link MaskType#CUSTOM}. Invalid or conflicting declarations redact only the
 * affected property.
 *
 * @author SummerWen
 * @since 0.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Mask {

    /**
     * Select the built-in or inline-pattern mode.
     * @return the selected mode, or {@link MaskType#UNSPECIFIED} for a custom
     * code
     */
    MaskType type() default MaskType.UNSPECIFIED;

    /**
     * Set the exact application-defined strategy code.
     * @return the custom code, without trimming or case normalization
     */
    String typeCode() default "";

    /**
     * Set the regular expression used by {@link MaskType#CUSTOM}.
     * @return the Java regular expression
     */
    String pattern() default "";

    /**
     * Set the replacement used by {@link MaskType#CUSTOM}.
     * @return the Java regular-expression replacement
     */
    String replacement() default "";
}
