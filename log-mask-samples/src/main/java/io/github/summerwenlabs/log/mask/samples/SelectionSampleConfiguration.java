package io.github.summerwenlabs.log.mask.samples;

import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.RestTemplateObservationConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Explicit RestTemplate selection samples run separately from the default-bean sample. */
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

@Component
@Profile("selection-demo")
final class SelectionSampleClient {

    private final RestTemplate annotated;
    private final RestTemplate byName;
    private final RestTemplate programmatic;
    private final RestTemplate shared;
    private final RestTemplate unselected;
    private final LocalSampleEndpoint endpoint;

    SelectionSampleClient(
            @Qualifier("annotated") RestTemplate annotated,
            @Qualifier("byName") RestTemplate byName,
            @Qualifier("programmatic") RestTemplate programmatic,
            @Qualifier("shared") RestTemplate shared,
            @Qualifier("unselected") RestTemplate unselected,
            LocalSampleEndpoint endpoint) {
        this.annotated = annotated;
        this.byName = byName;
        this.programmatic = programmatic;
        this.shared = shared;
        this.unselected = unselected;
        this.endpoint = endpoint;
    }

    ResponseEntity<Void> annotated() {
        return invoke(annotated, "annotated");
    }

    ResponseEntity<Void> byName() {
        return invoke(byName, "by-name");
    }

    ResponseEntity<Void> programmatic() {
        return invoke(programmatic, "programmatic");
    }

    ResponseEntity<Void> shared() {
        return invoke(shared, "shared");
    }

    ResponseEntity<Void> unselected() {
        return invoke(unselected, "unselected");
    }

    private ResponseEntity<Void> invoke(RestTemplate restTemplate, String selection) {
        return restTemplate.getForEntity(endpoint.selectionUri(selection), Void.class);
    }
}
