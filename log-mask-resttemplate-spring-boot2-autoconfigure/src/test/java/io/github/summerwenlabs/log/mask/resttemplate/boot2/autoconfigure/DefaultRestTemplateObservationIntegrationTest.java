package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class DefaultRestTemplateObservationIntegrationTest {

    private static final String EVENT_LOGGER_NAME = "log.mask.http";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class));
    private CapturedEvents events;

    @BeforeEach
    void captureEvents() {
        events = new CapturedEvents();
    }

    @AfterEach
    void releaseEvents() {
        events.close();
    }

    @Test
    void defaultRestTemplateLogsOneCompleteBodylessExchange() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.add("X-Response", "present");
            server.expect(once(), requestTo("https://API.EXAMPLE.COM:443/health?probe=ready"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("X-Request", "visible"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT).headers(responseHeaders));
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.add("X-Request", "visible");

            restTemplate.exchange(
                    "https://API.EXAMPLE.COM:443/health?probe=ready",
                    HttpMethod.GET,
                    new HttpEntity<Void>(requestHeaders),
                    Void.class);

            server.verify();
        });

        List<ILoggingEvent> loggedEvents = events.getEvents();
        assertEquals(1, loggedEvents.size());
        assertEquals(Level.INFO, loggedEvents.get(0).getLevel());
        assertCompleteBodylessEvent(loggedEvents.get(0).getFormattedMessage());
    }

    @Test
    void transportFailureLogsOneRequestOnlyEventWithoutChangingTheFailure() {
        IOException transportFailure = new IOException("transport unavailable");
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/unavailable"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(request -> {
                        throw transportFailure;
                    });

            ResourceAccessException actual = assertThrows(
                    ResourceAccessException.class,
                    () -> restTemplate.getForEntity(
                            "https://api.example.com/unavailable",
                            Void.class));

            assertSame(transportFailure, actual.getCause());
            server.verify();
        });

        assertEquals(1, events.getEvents().size());
        ILoggingEvent loggedEvent = events.getEvents().get(0);
        assertEquals(Level.INFO, loggedEvent.getLevel());
        JsonNode event = readEvent(loggedEvent.getFormattedMessage());
        assertEquals("GET", event.path("request").path("method").textValue());
        assertEquals(
                "https://api.example.com/unavailable",
                event.path("request").path("uri").path("full").textValue());
        assertTrue(event.path("response").isNull());
        assertFalse(event.has("outcome"));
        assertFalse(event.has("error"));
        assertFalse(event.has("exception"));
    }

    @Test
    void everyResponseStatusProducesExactlyOneInfoEvent() {
        List<HttpStatus> statuses = Arrays.asList(
                HttpStatus.NO_CONTENT,
                HttpStatus.NOT_MODIFIED,
                HttpStatus.BAD_REQUEST,
                HttpStatus.INTERNAL_SERVER_ERROR);
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            for (HttpStatus status : statuses) {
                server.expect(once(), requestTo(
                                "https://api.example.com/statuses/" + status.value()))
                        .andRespond(withStatus(status));
            }

            for (HttpStatus status : statuses) {
                String uri = "https://api.example.com/statuses/" + status.value();
                if (status.isError()) {
                    assertThrows(
                            RestClientResponseException.class,
                            () -> restTemplate.getForEntity(uri, Void.class));
                } else {
                    restTemplate.getForEntity(uri, Void.class);
                }
            }

            server.verify();
        });

        assertEquals(statuses.size(), events.getEvents().size());
        List<Integer> actualStatuses = new ArrayList<Integer>();
        Set<String> exchangeIds = new HashSet<String>();
        for (ILoggingEvent loggedEvent : events.getEvents()) {
            assertEquals(Level.INFO, loggedEvent.getLevel());
            JsonNode event = readEvent(loggedEvent.getFormattedMessage());
            actualStatuses.add(event.path("response").path("status").intValue());
            exchangeIds.add(event.path("exchangeId").textValue());
        }
        assertEquals(Arrays.asList(204, 304, 400, 500), actualStatuses);
        assertEquals(statuses.size(), exchangeIds.size());
    }

    @Test
    void defaultRestTemplateKeepsBuilderTransportConvertersAndInterceptors() {
        contextRunner.withUserConfiguration(HostRestTemplateConfiguration.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    HostTransport transport = context.getBean(HostTransport.class);
                    StringHttpMessageConverter converter =
                            context.getBean(StringHttpMessageConverter.class);

                    restTemplate.getForEntity(
                            "https://api.example.com/host-configured",
                            Void.class);

                    assertSame(converter, restTemplate.getMessageConverters().get(0));
                    assertEquals(
                            Arrays.asList(
                                    "interceptor-before",
                                    "factory-create",
                                    "factory-execute",
                                    "interceptor-after"),
                            transport.getCalls());
                    assertEquals("host", transport.getDownstreamHeader());
                });

        assertEquals(1, events.getEvents().size());
        JsonNode event = readEvent(events.getEvents().get(0).getFormattedMessage());
        assertTrue(event.path("durationMs").longValue() >= HostTransport.DELAY_MILLIS);
        assertFalse(hasName(event.path("request").path("headers"), "x-downstream"));
        assertEquals(
                Arrays.asList("configured"),
                valuesFor(event.path("response").path("headers"), "x-transport"));
    }

    private void assertCompleteBodylessEvent(String message) {
        JsonNode event = readEvent(message);

        assertFalse(message.contains("\n"));
        assertEquals("http_exchange", event.path("event").textValue());
        assertEquals(1, event.path("schemaVersion").intValue());
        assertNotNull(Instant.parse(event.path("timestamp").textValue()));
        assertEquals(4, UUID.fromString(event.path("exchangeId").textValue()).version());
        assertTrue(event.path("traceId").isNull());
        assertTrue(event.path("durationMs").isIntegralNumber());
        assertTrue(event.path("durationMs").longValue() >= 0);
        assertTrue(event.path("governanceEnabled").booleanValue());

        JsonNode request = event.path("request");
        assertEquals("GET", request.path("method").textValue());
        assertEquals("SUCCESS", request.path("uriState").textValue());
        assertEquals(
                "https://api.example.com:443/health?probe=ready",
                request.path("uri").path("full").textValue());
        assertEquals("SUCCESS", request.path("headersState").textValue());
        assertEquals(Arrays.asList("visible"), valuesFor(request.path("headers"), "x-request"));
        assertEquals("SUCCESS", request.path("bodyState").textValue());
        assertTrue(request.path("body").isNull());

        JsonNode response = event.path("response");
        assertEquals(204, response.path("status").intValue());
        assertEquals("SUCCESS", response.path("headersState").textValue());
        assertEquals(Arrays.asList("present"), valuesFor(response.path("headers"), "x-response"));
        assertEquals("SUCCESS", response.path("bodyState").textValue());
        assertTrue(response.path("body").isNull());
        assertFalse(event.has("outcome"));
        assertFalse(event.has("error"));
        assertFalse(response.has("statusText"));
    }

    private JsonNode readEvent(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    private static List<String> valuesFor(JsonNode entries, String name) {
        JsonNode entry = findEntry(entries, name);
        if (entry == null) {
            throw new AssertionError("missing name/value entry: " + name);
        }
        return Arrays.asList(objectValues(entry.path("values")));
    }

    private static boolean hasName(JsonNode entries, String name) {
        return findEntry(entries, name) != null;
    }

    private static JsonNode findEntry(JsonNode entries, String name) {
        for (JsonNode entry : entries) {
            if (name.equals(entry.path("name").textValue())) {
                return entry;
            }
        }
        return null;
    }

    private static String[] objectValues(JsonNode values) {
        String[] result = new String[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).isNull() ? null : values.get(index).textValue();
        }
        return result;
    }

    private static final class CapturedEvents implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(EVENT_LOGGER_NAME);
        private final Level originalLevel = logger.getLevel();
        private final boolean originalAdditive = logger.isAdditive();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();

        private CapturedEvents() {
            appender.start();
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
            logger.addAppender(appender);
        }

        private List<ILoggingEvent> getEvents() {
            return appender.list;
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
            appender.stop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HostRestTemplateConfiguration {

        @Bean
        HostTransport hostTransport() {
            return new HostTransport();
        }

        @Bean
        StringHttpMessageConverter hostConverter() {
            return new StringHttpMessageConverter();
        }

        @Bean
        RestTemplateCustomizer hostRestTemplateCustomizer(
                HostTransport transport,
                StringHttpMessageConverter converter) {
            return restTemplate -> {
                restTemplate.setRequestFactory(transport);
                restTemplate.getMessageConverters().add(0, converter);
                restTemplate.getInterceptors().add(transport.interceptor());
            };
        }
    }

    static final class HostTransport implements ClientHttpRequestFactory {
        private static final long DELAY_MILLIS = 25L;

        private final List<String> calls = new ArrayList<String>();
        private String downstreamHeader;

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            calls.add("factory-create");
            return new MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected ClientHttpResponse executeInternal() {
                    calls.add("factory-execute");
                    downstreamHeader = getHeaders().getFirst("X-Downstream");
                    MockClientHttpResponse response =
                            new MockClientHttpResponse(new byte[0], HttpStatus.NO_CONTENT);
                    response.getHeaders().add("X-Transport", "configured");
                    return response;
                }
            };
        }

        ClientHttpRequestInterceptor interceptor() {
            return new OrderedHostInterceptor(this);
        }

        List<String> getCalls() {
            return calls;
        }

        String getDownstreamHeader() {
            return downstreamHeader;
        }

        private static void delay() throws IOException {
            try {
                Thread.sleep(DELAY_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while simulating host interceptor work", exception);
            }
        }
    }

    static final class OrderedHostInterceptor implements ClientHttpRequestInterceptor, Ordered {
        private final HostTransport transport;

        private OrderedHostInterceptor(HostTransport transport) {
            this.transport = transport;
        }

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {
            transport.calls.add("interceptor-before");
            request.getHeaders().add("X-Downstream", "host");
            HostTransport.delay();
            ClientHttpResponse response = execution.execute(request, body);
            transport.calls.add("interceptor-after");
            return response;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
