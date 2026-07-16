package io.github.summerwenlabs.log.mask.resttemplate.boot2;

import org.springframework.web.client.RestTemplate;

/**
 * Selects RestTemplate instances for observation from Java configuration.
 *
 * <p>Selection is additive with annotation and bean-name configuration. The
 * callback must not replace the candidate instance and may be invoked after
 * singleton initialization.
 *
 * @author SummerWen
 * @since 0.1
 */
@FunctionalInterface
public interface RestTemplateObservationConfigurer {

    /**
     * Determine whether the supplied bean should be observed.
     * @param beanName the Spring bean name
     * @param restTemplate the candidate instance
     * @return {@code true} when the instance should be observed
     */
    boolean shouldObserve(String beanName, RestTemplate restTemplate);
}
