/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.samples.boot3;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Invokes the failing observed RestTemplate without consuming its exception.
 *
 * @author SummerWen
 * @since 0.1
 */
@Component
@Profile("request-only-demo")
final class RequestOnlySampleClient {

    private final RestTemplate restTemplate;
    private final LocalSampleEndpoint endpoint;

    RequestOnlySampleClient(
            @Qualifier("requestOnlyRestTemplate") RestTemplate restTemplate,
            LocalSampleEndpoint endpoint) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
    }

    void requestOnlyFailure() {
        restTemplate.getForEntity(endpoint.failureUri(), Void.class);
    }
}
