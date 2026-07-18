/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.integration;

import java.io.IOException;
import java.util.List;

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
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NestedExchangeLifecycleIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class));
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
    void synchronousNestedCallsProduceIndependentTerminalEvents() {
        contextRunner.withUserConfiguration(NestedTemplateConfiguration.class).run(context -> {
            RestTemplate restTemplate = context.getBean("nested", RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/inner"))
                    .andRespond(withSuccess("inner-result", org.springframework.http.MediaType.TEXT_PLAIN));
            server.expect(once(), requestTo("https://api.example.com/outer"))
                    .andRespond(withSuccess("outer-result", org.springframework.http.MediaType.TEXT_PLAIN));

            assertEquals("outer-result",
                    restTemplate.getForObject("https://api.example.com/outer", String.class));
            server.verify();
        });

        JsonNode inner = eventFor("https://api.example.com/inner");
        JsonNode outer = eventFor("https://api.example.com/outer");
        assertNotEquals(inner.path("exchangeId").textValue(), outer.path("exchangeId").textValue());
        assertEquals("inner-result", inner.path("response").path("body").textValue());
        assertEquals("outer-result", outer.path("response").path("body").textValue());
        assertEquals(2, events.getEvents().size());
    }

    @Test
    void anInnerTransportFailureDoesNotContaminateOuterOrSubsequentCalls() {
        contextRunner.withUserConfiguration(NestedTemplateConfiguration.class).run(context -> {
            RestTemplate restTemplate = context.getBean("nested", RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/outer-after-inner-failure"))
                    .andRespond(withSuccess("outer-result", org.springframework.http.MediaType.TEXT_PLAIN));
            server.expect(once(), requestTo("https://api.example.com/after"))
                    .andRespond(withSuccess("after-result", org.springframework.http.MediaType.TEXT_PLAIN));

            assertEquals("outer-result", restTemplate.getForObject(
                    "https://api.example.com/outer-after-inner-failure", String.class));
            assertEquals("after-result",
                    restTemplate.getForObject("https://api.example.com/after", String.class));
            server.verify();

            NestedCallInterceptor interceptor = context.getBean(NestedCallInterceptor.class);
            assertSame(interceptor.getInnerFailure(), interceptor.getObservedInnerFailure());
        });

        JsonNode failedInner = eventFor("https://api.example.com/inner-failure");
        assertTrue(failedInner.path("response").isNull());
        assertTrueResponse(eventFor("https://api.example.com/outer-after-inner-failure"), "outer-result");
        assertTrueResponse(eventFor("https://api.example.com/after"), "after-result");
        assertEquals(3, events.getEvents().size());
    }

    @Test
    void preservesTheExactErrorHandlerExceptionAndStillEmitsOneEvent() {
        contextRunner.withUserConfiguration(ThrowingErrorHandlerConfiguration.class).run(context -> {
            RestTemplate restTemplate = context.getBean("throwing", RestTemplate.class);
            ThrowingErrorHandler errorHandler = context.getBean(ThrowingErrorHandler.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/error"))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            RuntimeException actual = assertThrows(
                    RuntimeException.class,
                    () -> restTemplate.getForObject("https://api.example.com/error", String.class));
            assertSame(errorHandler.getFailure(), actual);
            server.verify();
        });

        JsonNode event = eventFor("https://api.example.com/error");
        assertEquals(500, event.path("response").path("status").intValue());
        assertEquals(1, events.getEvents().size());
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

    private void assertTrueResponse(JsonNode event, String body) {
        assertEquals(200, event.path("response").path("status").intValue());
        assertEquals(body, event.path("response").path("body").textValue());
    }

    private JsonNode readEvent(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception exception) {
            throw new AssertionError("event must be valid JSON", exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NestedTemplateConfiguration {

        @Bean
        NestedCallInterceptor nestedCallInterceptor() {
            return new NestedCallInterceptor();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate nested(NestedCallInterceptor interceptor) {
            RestTemplate restTemplate = new RestTemplate();
            interceptor.setRestTemplate(restTemplate);
            restTemplate.getInterceptors().add(interceptor);
            return restTemplate;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ThrowingErrorHandlerConfiguration {

        @Bean
        ThrowingErrorHandler throwingErrorHandler() {
            return new ThrowingErrorHandler();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate throwing(ThrowingErrorHandler errorHandler) {
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setErrorHandler(errorHandler);
            return restTemplate;
        }
    }

    static final class NestedCallInterceptor implements ClientHttpRequestInterceptor {

        private final IOException innerFailure = new IOException("inner transport failure");
        private RestTemplate restTemplate;
        private IOException observedInnerFailure;

        void setRestTemplate(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        IOException getInnerFailure() {
            return innerFailure;
        }

        IOException getObservedInnerFailure() {
            return observedInnerFailure;
        }

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {
            String path = request.getURI().getPath();
            if ("/outer".equals(path)) {
                restTemplate.getForObject("https://api.example.com/inner", String.class);
            } else if ("/outer-after-inner-failure".equals(path)) {
                try {
                    restTemplate.getForObject("https://api.example.com/inner-failure", String.class);
                } catch (ResourceAccessException exception) {
                    observedInnerFailure = (IOException) exception.getCause();
                }
            } else if ("/inner-failure".equals(path)) {
                throw innerFailure;
            }
            return execution.execute(request, body);
        }
    }

    static final class ThrowingErrorHandler implements ResponseErrorHandler {

        private final RuntimeException failure = new RuntimeException("business error handler failure");

        RuntimeException getFailure() {
            return failure;
        }

        @Override
        public boolean hasError(ClientHttpResponse response) {
            return true;
        }

        @Override
        public void handleError(ClientHttpResponse response) {
            throw failure;
        }
    }
}
