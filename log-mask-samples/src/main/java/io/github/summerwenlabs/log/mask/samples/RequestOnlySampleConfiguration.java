package io.github.summerwenlabs.log.mask.samples;

import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

/**
 * Configures a deterministic transport failure to demonstrate request-only
 * events.
 *
 * @author SummerWen
 * @since 0.1
 */
@Configuration(proxyBeanMethods = false)
@Profile("request-only-demo")
class RequestOnlySampleConfiguration {

    @Bean
    FailingClientHttpRequestFactory failingClientHttpRequestFactory() {
        return new FailingClientHttpRequestFactory();
    }

    @Bean
    @ObservedRestTemplate
    RestTemplate requestOnlyRestTemplate(FailingClientHttpRequestFactory requestFactory) {
        return new RestTemplate(requestFactory);
    }
}
