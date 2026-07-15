package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestTemplateRegionConfigurationIntegrationTest {

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
        MDC.clear();
        events.close();
    }

    @Test
    void governsRealExchangeRegionsWithoutChangingTheRequestOrResponse() {
        MDC.put("preferredTrace", "trace-from-host");
        contextRunner.withPropertyValues(
                        "log-mask.logging.rest-template.name-value-shape=COMPACT",
                        "log-mask.logging.rest-template.uri.details-enabled=false",
                        "log-mask.logging.rest-template.trace-id.mdc-keys[0]=preferredTrace",
                        "log-mask.logging.rest-template.trace-id.mdc-keys[1]=fallbackTrace",
                        "log-mask.governance.http.path.rules[0].pattern=/customers/{id}",
                        "log-mask.governance.http.path.rules[0].variables[0].name=id",
                        "log-mask.governance.http.path.rules[0].variables[0].type=REDACT",
                        "log-mask.governance.http.query.rules[0].name=token",
                        "log-mask.governance.http.query.rules[0].type=REDACT",
                        "log-mask.governance.http.headers.request.rules[0].name=X-Secret",
                        "log-mask.governance.http.headers.request.rules[0].type=REDACT",
                        "log-mask.governance.http.headers.response.rules[0].name=X-Response-Secret",
                        "log-mask.governance.http.headers.response.rules[0].type=REDACT")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.add("X-Response-Secret", "actual-response-header");
                    server.expect(once(), requestTo(
                                    "https://api.example.com/customers/42?token=actual&visible=ok"))
                            .andExpect(method(HttpMethod.POST))
                            .andExpect(header("X-Secret", "actual-header"))
                            .andRespond(withSuccess("real-response", org.springframework.http.MediaType.TEXT_PLAIN)
                                    .headers(responseHeaders));
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("X-Secret", "actual-header");

                    ResponseEntity<String> response = restTemplate.exchange(
                            "https://api.example.com/customers/42?token=actual&visible=ok",
                            HttpMethod.POST,
                            new HttpEntity<String>("real-request", headers),
                            String.class);

                    assertEquals("real-response", response.getBody());
                    assertEquals("actual-response-header", response.getHeaders()
                            .getFirst("X-Response-Secret"));
                    server.verify();
                });

        JsonNode event = onlyEvent();
        assertEquals("trace-from-host", event.path("traceId").textValue());
        assertEquals(
                "https://api.example.com/customers/%3Credacted%3E?token=%3Credacted%3E&visible=ok",
                event.path("request").path("uri").path("full").textValue());
        assertFalse(event.path("request").path("uri").has("path"));
        assertEquals("SUCCESS", event.path("request").path("uriState").textValue());
        assertEquals("<redacted>", event.path("request").path("headers")
                .path("x-secret").get(0).textValue());
        assertEquals("<redacted>", event.path("response").path("headers")
                .path("x-response-secret").get(0).textValue());
        assertEquals("real-request", event.path("request").path("body").textValue());
        assertEquals("real-response", event.path("response").path("body").textValue());
    }

    @Test
    void disablingGovernanceAndRegionsRetainsRawUriAndUsesDisabledStates() {
        MDC.put("traceId", "must-not-be-read");
        contextRunner.withPropertyValues(
                        "log-mask.governance.enabled=false",
                        "log-mask.logging.rest-template.trace-id.enabled=false",
                        "log-mask.logging.rest-template.request.headers-enabled=false",
                        "log-mask.logging.rest-template.request.body-enabled=false",
                        "log-mask.logging.rest-template.response.headers-enabled=false",
                        "log-mask.logging.rest-template.response.body-enabled=false",
                        "log-mask.governance.http.query.rules[0].name=token",
                        "log-mask.governance.http.query.rules[0].type=REDACT")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
                    server.expect(once(), requestTo("https://api.example.com/raw?token=actual"))
                            .andRespond(withSuccess("response", org.springframework.http.MediaType.TEXT_PLAIN));

                    restTemplate.postForEntity(
                            "https://api.example.com/raw?token=actual",
                            "request",
                            String.class);
                    server.verify();
                });

        JsonNode event = onlyEvent();
        assertFalse(event.path("governanceEnabled").booleanValue());
        assertTrue(event.path("traceId").isNull());
        assertEquals("https://api.example.com/raw?token=actual",
                event.path("request").path("uri").path("full").textValue());
        assertEquals("SUCCESS", event.path("request").path("uriState").textValue());
        assertEquals("DISABLED", event.path("request").path("headersState").textValue());
        assertTrue(event.path("request").path("headers").isNull());
        assertEquals("DISABLED", event.path("request").path("bodyState").textValue());
        assertEquals("", event.path("request").path("body").textValue());
        assertEquals("DISABLED", event.path("response").path("headersState").textValue());
        assertTrue(event.path("response").path("headers").isNull());
        assertEquals("DISABLED", event.path("response").path("bodyState").textValue());
        assertEquals("", event.path("response").path("body").textValue());
    }

    @Test
    void appliesConfiguredBodyBudgetToEachDirectionIndependently() {
        contextRunner.withPropertyValues(
                        "log-mask.logging.rest-template.max-body-size=8B")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
                    server.expect(once(), requestTo("https://api.example.com/budget"))
                            .andRespond(withSuccess("this response exceeds the budget",
                                    org.springframework.http.MediaType.TEXT_PLAIN));

                    restTemplate.postForEntity(
                            "https://api.example.com/budget",
                            "this request exceeds the budget",
                            String.class);
                    server.verify();
                });

        JsonNode event = onlyEvent();
        assertEquals("LIMIT_EXCEEDED", event.path("request").path("bodyState").textValue());
        assertEquals("", event.path("request").path("body").textValue());
        assertEquals("LIMIT_EXCEEDED", event.path("response").path("bodyState").textValue());
        assertEquals("", event.path("response").path("body").textValue());
    }

    @Test
    void rejectsAZeroBodyBudgetAtStartup() {
        contextRunner.withPropertyValues("log-mask.logging.rest-template.max-body-size=0B")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(failure.getMessage().contains("max-body-size"));
                });
    }

    private JsonNode onlyEvent() {
        List<ch.qos.logback.classic.spi.ILoggingEvent> loggedEvents = events.getEvents();
        assertEquals(1, loggedEvents.size());
        try {
            return objectMapper.readTree(loggedEvents.get(0).getFormattedMessage());
        } catch (Exception exception) {
            throw new AssertionError("event must be valid JSON", exception);
        }
    }
}
