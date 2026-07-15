package io.github.summerwenlabs.log.mask.samples;

import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.stereotype.Component;

/** Builds local URLs only after the embedded server has selected its actual port. */
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
