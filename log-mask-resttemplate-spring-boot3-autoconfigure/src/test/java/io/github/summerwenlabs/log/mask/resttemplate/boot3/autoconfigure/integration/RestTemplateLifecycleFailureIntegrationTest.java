/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.integration;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.CapturedHttpEvents;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.LogMaskRestTemplateAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestTemplateLifecycleFailureIntegrationTest {

    private static final String BASE_URI = "https://api.example.com";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class))
            .withUserConfiguration(LifecycleConfiguration.class);
    private CapturedHttpEvents events;

    @BeforeEach
    void captureEvents() {
        events = new CapturedHttpEvents();
    }

    @AfterEach
    void releaseEvents() {
        events.close();
    }

    @Test
    void requestConverterFailurePreservesItsInstanceAndLeavesNoExchangeState() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            FailingStringHttpMessageConverter converter =
                    context.getBean(FailingStringHttpMessageConverter.class);
            converter.failNextWrite();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);

            HttpMessageNotWritableException actual = assertThrows(
                    HttpMessageNotWritableException.class,
                    () -> restTemplate.postForObject(
                            BASE_URI + "/request-converter-failure",
                            new HttpEntity<String>("request", headers),
                            String.class));
            assertSame(converter.getWriteFailure(), actual);

            assertEquals("after-request-converter-failure", restTemplate.getForObject(
                    BASE_URI + "/after-request-converter-failure", String.class));
        });

        JsonNode failed = eventFor(BASE_URI + "/request-converter-failure");
        assertTrue(failed.path("response").isNull());
        assertEquals("PROCESSING_FAILED", failed.path("request").path("bodyState").textValue());
        assertSuccessfulResponse(
                eventFor(BASE_URI + "/after-request-converter-failure"),
                "after-request-converter-failure");
        assertEquals(2, events.getEvents().size());
    }

    @Test
    void responseConverterFailurePreservesItsInstanceAndCleansUpForTheNextExchange() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            FailingStringHttpMessageConverter converter =
                    context.getBean(FailingStringHttpMessageConverter.class);
            converter.failNextRead();

            RestClientException actual = assertThrows(
                    RestClientException.class,
                    () -> restTemplate.getForObject(
                            BASE_URI + "/response-converter-failure", String.class));
            assertSame(converter.getReadFailure(), actual.getCause());

            assertEquals("after-response-converter-failure", restTemplate.getForObject(
                    BASE_URI + "/after-response-converter-failure", String.class));
        });

        JsonNode failed = eventFor(BASE_URI + "/response-converter-failure");
        assertEquals(200, failed.path("response").path("status").intValue());
        assertSuccessfulResponse(
                eventFor(BASE_URI + "/after-response-converter-failure"),
                "after-response-converter-failure");
        assertEquals(2, events.getEvents().size());
    }

    @Test
    void downstreamInterceptorFailurePreservesItsInstanceAndCleansUpForTheNextExchange() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ThrowingDownstreamInterceptor interceptor =
                    context.getBean(ThrowingDownstreamInterceptor.class);

            RuntimeException actual = assertThrows(
                    RuntimeException.class,
                    () -> restTemplate.getForObject(
                            BASE_URI + "/downstream-interceptor-failure", String.class));
            assertSame(interceptor.getFailure(), actual);

            assertEquals("after-downstream-interceptor-failure", restTemplate.getForObject(
                    BASE_URI + "/after-downstream-interceptor-failure", String.class));
        });

        assertTrue(eventFor(BASE_URI + "/downstream-interceptor-failure")
                .path("response").isNull());
        assertSuccessfulResponse(
                eventFor(BASE_URI + "/after-downstream-interceptor-failure"),
                "after-downstream-interceptor-failure");
        assertEquals(2, events.getEvents().size());
    }

    @Test
    void concurrentCallsAndSingleWorkerReuseKeepExchangeScopesIndependent() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory factory = context.getBean(ControlledResponseFactory.class);
            factory.blockNextTwoRequests();

            ExecutorService concurrent = Executors.newFixedThreadPool(2);
            try {
                Future<String> first = concurrent.submit(new GetForObjectTask(
                        restTemplate, BASE_URI + "/concurrent/first"));
                Future<String> second = concurrent.submit(new GetForObjectTask(
                        restTemplate, BASE_URI + "/concurrent/second"));
                assertTrue(factory.awaitBlockedRequests());
                factory.releaseBlockedRequests();
                assertEquals("first", await(first));
                assertEquals("second", await(second));
            } finally {
                factory.releaseBlockedRequests();
                concurrent.shutdownNow();
            }

            ExecutorService reusedWorker = Executors.newSingleThreadExecutor();
            try {
                Future<Throwable> failed = reusedWorker.submit(new FailingGetTask(
                        restTemplate, BASE_URI + "/thread-reuse-failure"));
                Throwable actual = await(failed);
                assertTrue(actual instanceof ResourceAccessException);
                assertSame(factory.getThreadReuseFailure(), actual.getCause());
                assertEquals("after-thread-reuse-failure", await(reusedWorker.submit(
                        new GetForObjectTask(
                                restTemplate, BASE_URI + "/after-thread-reuse-failure"))));
            } finally {
                reusedWorker.shutdownNow();
            }
        });

        assertSuccessfulResponse(eventFor(BASE_URI + "/concurrent/first"), "first");
        assertSuccessfulResponse(eventFor(BASE_URI + "/concurrent/second"), "second");
        assertTrue(eventFor(BASE_URI + "/thread-reuse-failure")
                .path("response").isNull());
        assertSuccessfulResponse(
                eventFor(BASE_URI + "/after-thread-reuse-failure"),
                "after-thread-reuse-failure");
        assertEquals(4, events.getEvents().size());
    }

    private JsonNode eventFor(String uri) {
        List<ch.qos.logback.classic.spi.ILoggingEvent> loggedEvents = events.getEvents();
        for (ch.qos.logback.classic.spi.ILoggingEvent loggedEvent : loggedEvents) {
            JsonNode event = readEvent(loggedEvent.getFormattedMessage());
            if (uri.equals(event.path("request").path("uri").path("full").textValue())) {
                return event;
            }
        }
        throw new AssertionError("missing event for " + uri);
    }

    private void assertSuccessfulResponse(JsonNode event, String body) {
        assertEquals(200, event.path("response").path("status").intValue());
        assertEquals("SUCCESS", event.path("response").path("bodyState").textValue());
        assertEquals(body, event.path("response").path("body").textValue());
    }

    private JsonNode readEvent(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception exception) {
            throw new AssertionError("event must be valid JSON", exception);
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("background RestTemplate call was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("background RestTemplate call failed", exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LifecycleConfiguration {

        @Bean
        ControlledResponseFactory controlledResponseFactory() {
            return new ControlledResponseFactory();
        }

        @Bean
        FailingStringHttpMessageConverter failingStringHttpMessageConverter() {
            return new FailingStringHttpMessageConverter();
        }

        @Bean
        ThrowingDownstreamInterceptor throwingDownstreamInterceptor() {
            return new ThrowingDownstreamInterceptor();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate lifecycleRestTemplate(
                ControlledResponseFactory requestFactory,
                FailingStringHttpMessageConverter converter,
                ThrowingDownstreamInterceptor interceptor) {
            RestTemplate restTemplate = new RestTemplate(
                    Collections.<HttpMessageConverter<?>>singletonList(converter));
            restTemplate.setRequestFactory(requestFactory);
            restTemplate.getInterceptors().add(interceptor);
            return restTemplate;
        }
    }

    static final class FailingStringHttpMessageConverter extends StringHttpMessageConverter {

        private final HttpMessageNotWritableException writeFailure =
                new HttpMessageNotWritableException("request converter failure");
        private final HttpMessageNotReadableException readFailure =
                new HttpMessageNotReadableException("response converter failure");
        private boolean failWrite;
        private boolean failRead;

        void failNextWrite() {
            failWrite = true;
        }

        void failNextRead() {
            failRead = true;
        }

        HttpMessageNotWritableException getWriteFailure() {
            return writeFailure;
        }

        HttpMessageNotReadableException getReadFailure() {
            return readFailure;
        }

        @Override
        protected String readInternal(
                Class<? extends String> clazz,
                HttpInputMessage inputMessage) throws IOException {
            if (failRead) {
                failRead = false;
                throw readFailure;
            }
            return super.readInternal(clazz, inputMessage);
        }

        @Override
        protected void writeInternal(
                String value,
                HttpOutputMessage outputMessage) throws IOException {
            if (failWrite) {
                failWrite = false;
                throw writeFailure;
            }
            super.writeInternal(value, outputMessage);
        }
    }

    static final class ThrowingDownstreamInterceptor implements ClientHttpRequestInterceptor {

        private final RuntimeException failure =
                new IllegalStateException("downstream interceptor failure");

        RuntimeException getFailure() {
            return failure;
        }

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {
            if ("/downstream-interceptor-failure".equals(request.getURI().getPath())) {
                throw failure;
            }
            return execution.execute(request, body);
        }
    }

    static final class ControlledResponseFactory implements ClientHttpRequestFactory {

        private final IOException threadReuseFailure =
                new IOException("thread reuse transport failure");
        private volatile CountDownLatch blockedRequests;
        private volatile CountDownLatch releaseBlockedRequests;

        void blockNextTwoRequests() {
            blockedRequests = new CountDownLatch(2);
            releaseBlockedRequests = new CountDownLatch(1);
        }

        boolean awaitBlockedRequests() {
            CountDownLatch blocked = blockedRequests;
            if (blocked == null) {
                return false;
            }
            try {
                return blocked.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("waiting for concurrent calls was interrupted", exception);
            }
        }

        void releaseBlockedRequests() {
            CountDownLatch release = releaseBlockedRequests;
            if (release != null) {
                release.countDown();
            }
        }

        IOException getThreadReuseFailure() {
            return threadReuseFailure;
        }

        @Override
        public ClientHttpRequest createRequest(final URI uri, HttpMethod httpMethod) {
            return new MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected ClientHttpResponse executeInternal() throws IOException {
                    awaitConcurrentRequests();
                    if ("/thread-reuse-failure".equals(uri.getPath())) {
                        throw threadReuseFailure;
                    }
                    String body = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
                    MockClientHttpResponse response = new MockClientHttpResponse(
                            body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
                    response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                    return response;
                }
            };
        }

        private void awaitConcurrentRequests() throws IOException {
            CountDownLatch blocked = blockedRequests;
            CountDownLatch release = releaseBlockedRequests;
            if (blocked == null || release == null) {
                return;
            }
            blocked.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("concurrent requests were not released");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("concurrent request was interrupted", exception);
            }
        }
    }

    private static final class GetForObjectTask implements Callable<String> {

        private final RestTemplate restTemplate;
        private final String uri;

        private GetForObjectTask(RestTemplate restTemplate, String uri) {
            this.restTemplate = restTemplate;
            this.uri = uri;
        }

        @Override
        public String call() {
            return restTemplate.getForObject(uri, String.class);
        }
    }

    private static final class FailingGetTask implements Callable<Throwable> {

        private final RestTemplate restTemplate;
        private final String uri;

        private FailingGetTask(RestTemplate restTemplate, String uri) {
            this.restTemplate = restTemplate;
            this.uri = uri;
        }

        @Override
        public Throwable call() {
            try {
                restTemplate.getForObject(uri, String.class);
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        }
    }
}
