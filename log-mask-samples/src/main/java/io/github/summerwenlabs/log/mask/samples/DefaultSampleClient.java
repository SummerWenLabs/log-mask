package io.github.summerwenlabs.log.mask.samples;

import java.util.Arrays;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Calls local endpoints with the auto-configured default observed RestTemplate.
 *
 * <p>The scenarios cover typed JSON, string, byte-array, and absent bodies
 * without relying on an external service.
 *
 * @author SummerWen
 * @since 0.1
 */
@Component
@Profile("!selection-demo & !request-only-demo")
final class DefaultSampleClient {

    private final RestTemplate restTemplate;
    private final LocalSampleEndpoint endpoint;

    DefaultSampleClient(RestTemplate restTemplate, LocalSampleEndpoint endpoint) {
        this.restTemplate = restTemplate;
        this.endpoint = endpoint;
    }

    ResponseEntity<SamplePayload> typedJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Secret", SampleValues.REQUEST_SECRET);
        SamplePayload request = new SamplePayload(
                "request", SampleValues.REQUEST_PHONE, SampleValues.REQUEST_INTERNAL);
        return restTemplate.exchange(
                endpoint.customerUri(),
                HttpMethod.POST,
                new HttpEntity<SamplePayload>(request, headers),
                SamplePayload.class);
    }

    ResponseEntity<String> stringBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        return restTemplate.exchange(
                endpoint.stringsUri(),
                HttpMethod.POST,
                new HttpEntity<String>(SampleValues.JSON_LOOKING_STRING, headers),
                String.class);
    }

    ResponseEntity<byte[]> byteArrayBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        byte[] request = Arrays.copyOf(SampleValues.BYTES, SampleValues.BYTES.length);
        return restTemplate.exchange(
                endpoint.bytesUri(),
                HttpMethod.POST,
                new HttpEntity<byte[]>(request, headers),
                byte[].class);
    }

    ResponseEntity<Void> noBody() {
        return restTemplate.getForEntity(endpoint.noBodyUri(), Void.class);
    }
}
