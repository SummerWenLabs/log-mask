package io.github.summerwenlabs.log.mask.samples;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.ObservedRestTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/** Uses a deterministic transport failure to demonstrate a request-only event. */
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

/** Exposes the failure call without catching or replacing the business exception. */
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

final class FailingClientHttpRequestFactory implements ClientHttpRequestFactory {

    private final IOException failure = new IOException("sample transport failure");

    IOException failure() {
        return failure;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
        return new AbstractClientHttpRequest() {
            @Override
            public HttpMethod getMethod() {
                return httpMethod;
            }

            @Override
            public String getMethodValue() {
                return httpMethod.name();
            }

            @Override
            public URI getURI() {
                return uri;
            }

            @Override
            protected OutputStream getBodyInternal(HttpHeaders headers) {
                return new ByteArrayOutputStream();
            }

            @Override
            protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
                throw failure;
            }
        };
    }
}
