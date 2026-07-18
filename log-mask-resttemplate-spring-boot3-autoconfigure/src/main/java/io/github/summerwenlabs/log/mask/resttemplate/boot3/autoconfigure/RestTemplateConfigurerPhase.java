/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.summerwenlabs.log.mask.resttemplate.boot3.RestTemplateObservationConfigurer;
import org.springframework.web.client.RestTemplate;

/**
 * Publishes resolved configurers while preserving every candidate seen before
 * the single phase transition for later draining.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RestTemplateConfigurerPhase {

    private List<Candidate> pendingCandidates = new ArrayList<Candidate>();
    private List<RestTemplateObservationConfigurer> resolvedConfigurers;

    synchronized List<RestTemplateObservationConfigurer> submit(
            String beanName,
            RestTemplate restTemplate) {
        if (resolvedConfigurers == null) {
            pendingCandidates.add(new Candidate(beanName, restTemplate));
            return null;
        }
        return resolvedConfigurers;
    }

    synchronized List<Candidate> resolve(
            List<RestTemplateObservationConfigurer> configurers) {
        if (resolvedConfigurers != null) {
            throw new IllegalStateException("RestTemplate configurers are already resolved");
        }
        resolvedConfigurers = Collections.unmodifiableList(
                new ArrayList<RestTemplateObservationConfigurer>(configurers));
        List<Candidate> drainedCandidates = pendingCandidates;
        pendingCandidates = null;
        return drainedCandidates;
    }

    /** A candidate observed before configurer resolution completed. */
    static final class Candidate {

        private final String beanName;
        private final RestTemplate restTemplate;

        private Candidate(String beanName, RestTemplate restTemplate) {
            this.beanName = beanName;
            this.restTemplate = restTemplate;
        }

        String getBeanName() {
            return beanName;
        }

        RestTemplate getRestTemplate() {
            return restTemplate;
        }
    }
}
