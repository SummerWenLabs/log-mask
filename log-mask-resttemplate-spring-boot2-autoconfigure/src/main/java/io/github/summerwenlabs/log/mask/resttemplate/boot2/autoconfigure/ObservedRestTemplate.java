package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a RestTemplate bean for explicit HTTP exchange observation.
 *
 * <p>The annotation selects an existing bean; it does not proxy invocations or
 * alter the bean's request factory, transport settings, or error handler.
 *
 * @author SummerWen
 * @since 0.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ObservedRestTemplate {
}
