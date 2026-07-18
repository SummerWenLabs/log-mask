/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.samples.boot3;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the failure call without catching or replacing the business
 * exception.
 *
 * @author SummerWen
 * @since 0.1
 */
@RestController
@Profile("request-only-demo")
final class RequestOnlyDemoEndpoint {

    private final RequestOnlySampleClient client;

    RequestOnlyDemoEndpoint(RequestOnlySampleClient client) {
        this.client = client;
    }

    @GetMapping("/samples/request-only")
    void requestOnlyFailure() {
        client.requestOnlyFailure();
    }
}
