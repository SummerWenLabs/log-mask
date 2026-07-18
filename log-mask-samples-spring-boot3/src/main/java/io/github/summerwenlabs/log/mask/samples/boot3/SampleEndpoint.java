/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.samples.boot3;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides local downstream endpoints for real HTTP sample calls.
 *
 * <p>Captured inputs let integration tests prove that log governance never
 * changes the values received by the downstream application.
 *
 * @author SummerWen
 * @since 0.1
 */
@RestController
final class SampleEndpoint {

    private volatile CustomerRequest lastCustomerRequest;
    private volatile String lastStringBody;
    private volatile byte[] lastBytesBody;

    @PostMapping(
            value = "/samples/customers/{customerId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SamplePayload> customer(
            @PathVariable("customerId") String customerId,
            @RequestParam("token") String token,
            @RequestHeader("X-Secret") String secret,
            @RequestBody SamplePayload payload) {
        lastCustomerRequest = new CustomerRequest(customerId, token, secret, payload);
        return ResponseEntity.ok()
                .header("X-Response-Secret", SampleValues.RESPONSE_SECRET)
                .body(new SamplePayload("response", "13900139000", "response-internal"));
    }

    @PostMapping(
            value = "/samples/strings",
            consumes = MediaType.TEXT_PLAIN_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    String stringBody(@RequestBody String body) {
        lastStringBody = body;
        return body;
    }

    @PostMapping(
            value = "/samples/bytes",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] bytesBody(@RequestBody byte[] body) {
        lastBytesBody = Arrays.copyOf(body, body.length);
        return Arrays.copyOf(body, body.length);
    }

    @GetMapping("/samples/no-body")
    ResponseEntity<Void> noBody() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/samples/selection/{selection}")
    ResponseEntity<Void> selection(@PathVariable("selection") String selection) {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/samples/failure")
    ResponseEntity<Void> failure() {
        return ResponseEntity.noContent().build();
    }

    CustomerRequest lastCustomerRequest() {
        return lastCustomerRequest;
    }

    String lastStringBody() {
        return lastStringBody;
    }

    byte[] lastBytesBody() {
        return lastBytesBody == null ? null : Arrays.copyOf(lastBytesBody, lastBytesBody.length);
    }

    static final class CustomerRequest {
        private final String customerId;
        private final String token;
        private final String secret;
        private final SamplePayload payload;

        private CustomerRequest(
                String customerId,
                String token,
                String secret,
                SamplePayload payload) {
            this.customerId = customerId;
            this.token = token;
            this.secret = secret;
            this.payload = payload;
        }

        String getCustomerId() {
            return customerId;
        }

        String getToken() {
            return token;
        }

        String getSecret() {
            return secret;
        }

        SamplePayload getPayload() {
            return payload;
        }
    }
}
