/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.samples.boot3;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Builds local URLs after the embedded server selects its actual port.
 *
 * @author SummerWen
 * @since 0.1
 */
@Component
final class LocalSampleEndpoint {

    private final WebServerApplicationContext webServerApplicationContext;

    LocalSampleEndpoint(WebServerApplicationContext webServerApplicationContext) {
        this.webServerApplicationContext = webServerApplicationContext;
    }

    String customerUri() {
        return baseUrl() + "/samples/customers/" + SampleValues.CUSTOMER_ID
                + "?token=" + SampleValues.TOKEN + "&visible=ok";
    }

    String stringsUri() {
        return baseUrl() + "/samples/strings";
    }

    String bytesUri() {
        return baseUrl() + "/samples/bytes";
    }

    String noBodyUri() {
        return baseUrl() + "/samples/no-body";
    }

    String selectionUri(String selection) {
        return baseUrl() + "/samples/selection/" + selection;
    }

    String failureUri() {
        return baseUrl() + "/samples/failure";
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + webServerApplicationContext.getWebServer().getPort();
    }
}
