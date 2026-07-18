/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.samples.boot3;

import io.github.summerwenlabs.log.mask.resttemplate.boot3.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.RestTemplateObservationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * Configures annotation, bean-name, and programmatic RestTemplate selection.
 *
 * <p>The shared instance is selected twice to demonstrate identity-based
 * idempotence, while the unselected instance remains unchanged.
 *
 * @author SummerWen
 * @since 0.1
 */
@Configuration(proxyBeanMethods = false)
@Profile("selection-demo")
class SelectionSampleConfiguration {

    @Bean
    @ObservedRestTemplate
    RestTemplate annotated() {
        return new RestTemplate();
    }

    @Bean
    RestTemplate byName() {
        return new RestTemplate();
    }

    @Bean
    RestTemplate programmatic() {
        return new RestTemplate();
    }

    @Bean
    @ObservedRestTemplate
    RestTemplate shared() {
        return new RestTemplate();
    }

    @Bean
    RestTemplate unselected() {
        return new RestTemplate();
    }

    @Bean
    RestTemplateObservationConfigurer sampleSelectionConfigurer() {
        return (beanName, restTemplate) -> "programmatic".equals(beanName)
                || "shared".equals(beanName);
    }
}
