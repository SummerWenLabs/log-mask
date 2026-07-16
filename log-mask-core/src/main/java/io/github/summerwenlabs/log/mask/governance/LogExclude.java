package io.github.summerwenlabs.log.mask.governance;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a Jackson logical property from log-only JSON representations.
 *
 * <p>The property remains available to the application's normal Jackson
 * configuration. Safe representation generation does not read its value.
 *
 * @author SummerWen
 * @since 0.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface LogExclude {
}
