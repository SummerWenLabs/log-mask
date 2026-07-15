package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.web.client.RestTemplate;

/** Selects RestTemplate instances for observation from Java configuration. */
@FunctionalInterface
public interface RestTemplateObservationConfigurer {

    /**
     * Returns whether the supplied bean should be observed.
     *
     * @param beanName the Spring bean name
     * @param restTemplate the candidate instance
     * @return true when the instance should be observed
     */
    boolean shouldObserve(String beanName, RestTemplate restTemplate);
}
