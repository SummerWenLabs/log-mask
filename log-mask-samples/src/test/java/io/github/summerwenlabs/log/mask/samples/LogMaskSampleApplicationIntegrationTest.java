/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.samples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

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
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskSampleApplicationIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ConfigurableApplicationContext context;
    private CapturedHttpEvents events;

    @BeforeEach
    void captureEvents() {
        events = null;
    }

    @AfterEach
    void closeContextAndCapture() {
        if (context != null) {
            context.close();
        }
        if (events != null) {
            events.close();
        }
    }

    @Test
    void defaultObservedTemplateCallsTheLocalEndpointWithGovernedTypedAndUntypedBodies() {
        start();
        assertNotNull(context.getBean("logMaskRestTemplate", RestTemplate.class));
        DefaultSampleClient client = context.getBean(DefaultSampleClient.class);
        SampleEndpoint endpoint = context.getBean(SampleEndpoint.class);

        events.clear();
        ResponseEntity<SamplePayload> typedResponse = client.typedJson();

        assertEquals("13900139000", typedResponse.getBody().getPhone());
        assertEquals("response-internal", typedResponse.getBody().getInternal());
        assertEquals(SampleValues.CUSTOMER_ID, endpoint.lastCustomerRequest().getCustomerId());
        assertEquals(SampleValues.TOKEN, endpoint.lastCustomerRequest().getToken());
        assertEquals(SampleValues.REQUEST_SECRET, endpoint.lastCustomerRequest().getSecret());
        assertEquals(SampleValues.REQUEST_PHONE, endpoint.lastCustomerRequest().getPayload().getPhone());
        assertEquals(SampleValues.REQUEST_INTERNAL,
                endpoint.lastCustomerRequest().getPayload().getInternal());
        assertEquals(SampleValues.RESPONSE_SECRET,
                typedResponse.getHeaders().getFirst("X-Response-Secret"));

        JsonNode typedEvent = onlyCompactEvent();
        assertTrue(typedEvent.path("request").path("uri").path("full").textValue()
                .endsWith("/samples/customers/%3Credacted%3E?token=%3Credacted%3E&visible=ok"));
        assertEquals("<redacted>", typedEvent.path("request").path("headers")
                .path("x-secret").get(0).textValue());
        assertEquals("<redacted>", typedEvent.path("response").path("headers")
                .path("x-response-secret").get(0).textValue());
        assertEquals("138****8000", typedEvent.path("request").path("body")
                .path("phone").textValue());
        assertFalse(typedEvent.path("request").path("body").has("internal"));
        assertEquals("139****9000", typedEvent.path("response").path("body")
                .path("phone").textValue());
        assertFalse(typedEvent.path("response").path("body").has("internal"));

        events.clear();
        ResponseEntity<String> stringResponse = client.stringBody();

        assertEquals(SampleValues.JSON_LOOKING_STRING, stringResponse.getBody());
        assertEquals(SampleValues.JSON_LOOKING_STRING, endpoint.lastStringBody());
        JsonNode stringEvent = onlyCompactEvent();
        assertTrue(stringEvent.path("request").path("body").isTextual());
        assertEquals(SampleValues.JSON_LOOKING_STRING,
                stringEvent.path("request").path("body").textValue());
        assertTrue(stringEvent.path("response").path("body").isTextual());
        assertEquals(SampleValues.JSON_LOOKING_STRING,
                stringEvent.path("response").path("body").textValue());

        events.clear();
        ResponseEntity<byte[]> bytesResponse = client.byteArrayBody();

        assertArrayEquals(SampleValues.BYTES, bytesResponse.getBody());
        assertArrayEquals(SampleValues.BYTES, endpoint.lastBytesBody());
        JsonNode bytesEvent = onlyCompactEvent();
        assertEquals(Base64.getEncoder().encodeToString(SampleValues.BYTES),
                bytesEvent.path("request").path("body").textValue());
        assertEquals(Base64.getEncoder().encodeToString(SampleValues.BYTES),
                bytesEvent.path("response").path("body").textValue());

        events.clear();
        ResponseEntity<Void> noBodyResponse = client.noBody();

        assertEquals(204, noBodyResponse.getStatusCodeValue());
        JsonNode noBodyEvent = onlyCompactEvent();
        assertTrue(noBodyEvent.path("request").path("body").isNull());
        assertTrue(noBodyEvent.path("response").path("body").isNull());
        assertEquals("SUCCESS", noBodyEvent.path("request").path("bodyState").textValue());
        assertEquals("SUCCESS", noBodyEvent.path("response").path("bodyState").textValue());
    }

    @Test
    void regionAndGovernanceProfilesShowTheirDifferentOutputSemantics() {
        start("log-mask.logging.rest-template.request.body-enabled=false");
        DefaultSampleClient client = context.getBean(DefaultSampleClient.class);

        events.clear();
        client.typedJson();

        JsonNode regionDisabled = onlyCompactEvent();
        assertEquals("DISABLED", regionDisabled.path("request").path("bodyState").textValue());
        assertEquals("", regionDisabled.path("request").path("body").textValue());
        assertEquals("SUCCESS", regionDisabled.path("response").path("bodyState").textValue());

        context.close();
        context = null;
        events.clear();
        start("log-mask.governance.enabled=false");
        client = context.getBean(DefaultSampleClient.class);

        events.clear();
        client.typedJson();

        JsonNode raw = onlyCompactEvent();
        assertFalse(raw.path("governanceEnabled").booleanValue());
        assertEquals("SUCCESS", raw.path("request").path("headersState").textValue());
        assertEquals("SUCCESS", raw.path("request").path("bodyState").textValue());
        assertTrue(raw.path("request").path("uri").path("full").textValue()
                .endsWith("/samples/customers/customer-42?token=actual-token&visible=ok"));
        assertEquals(SampleValues.REQUEST_SECRET, raw.path("request").path("headers")
                .path("x-secret").get(0).textValue());
        assertEquals(SampleValues.REQUEST_PHONE, raw.path("request").path("body")
                .path("phone").textValue());
        assertEquals(SampleValues.REQUEST_INTERNAL, raw.path("request").path("body")
                .path("internal").textValue());
        assertEquals(SampleValues.RESPONSE_SECRET, raw.path("response").path("headers")
                .path("x-response-secret").get(0).textValue());
        assertEquals("13900139000", raw.path("response").path("body")
                .path("phone").textValue());
        assertEquals("response-internal", raw.path("response").path("body")
                .path("internal").textValue());
    }

    @Test
    void explicitSelectionProfileUsesAnnotationBeanNameConfigurerAndIdentityDeduplication() {
        startWithProfiles(new String[] {"selection-demo"});
        SelectionSampleClient client = context.getBean(SelectionSampleClient.class);

        events.clear();
        client.annotated();
        onlyCompactEvent();

        events.clear();
        client.byName();
        onlyCompactEvent();

        events.clear();
        client.programmatic();
        onlyCompactEvent();

        RestTemplate shared = context.getBean("shared", RestTemplate.class);
        assertEquals(1, shared.getInterceptors().size());
        events.clear();
        client.shared();
        onlyCompactEvent();

        events.clear();
        client.unselected();
        assertEquals(0, events.snapshot().size());
    }

    @Test
    void requestOnlyFailureKeepsTheOriginalCauseAndWritesOneNullResponseEvent() {
        startWithProfiles(new String[] {"request-only-demo"});
        RequestOnlySampleClient client = context.getBean(RequestOnlySampleClient.class);
        FailingClientHttpRequestFactory factory = context.getBean(FailingClientHttpRequestFactory.class);

        events.clear();
        ResourceAccessException failure = assertThrows(
                ResourceAccessException.class,
                client::requestOnlyFailure);

        assertSame(factory.failure(), failure.getCause());
        JsonNode event = onlyCompactEvent();
        assertTrue(event.path("response").isNull());
        assertTrue(event.path("request").path("uri").path("full").textValue()
                .endsWith("/samples/failure"));
    }

    private void start(String... properties) {
        startWithProfiles(new String[0], properties);
    }

    private void startWithProfiles(String[] profiles, String... properties) {
        List<String> values = new ArrayList<String>();
        values.add("server.port=0");
        values.add("log-mask.samples.demo.enabled=false");
        values.add("logging.level.log.mask.http=INFO");
        values.addAll(Arrays.asList(properties));
        context = new SpringApplicationBuilder(LogMaskSampleApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(profiles)
                .properties(values.toArray(new String[values.size()]))
                .run();
        resetEventCapture();
    }

    private void resetEventCapture() {
        if (events != null) {
            events.close();
        }
        events = new CapturedHttpEvents();
    }

    private JsonNode onlyCompactEvent() {
        List<ILoggingEvent> loggedEvents = events.snapshot();
        assertEquals(1, loggedEvents.size());
        ILoggingEvent loggedEvent = loggedEvents.get(0);
        assertEquals(Level.INFO, loggedEvent.getLevel());
        String message = loggedEvent.getFormattedMessage();
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("\r"));
        try {
            JsonNode event = objectMapper.readTree(message);
            assertEquals("http_exchange", event.path("event").textValue());
            assertEquals(1, event.path("schemaVersion").intValue());
            assertTrue(event.path("request").path("headers").isObject());
            return event;
        } catch (Exception exception) {
            throw new AssertionError("log.mask.http must contain compact valid JSON", exception);
        }
    }

    private static final class CapturedHttpEvents implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger("log.mask.http");
        private final Level originalLevel = logger.getLevel();
        private final boolean originalAdditive = logger.isAdditive();
        private final ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();

        private CapturedHttpEvents() {
            appender.start();
            logger.setLevel(Level.INFO);
            logger.setAdditive(false);
            logger.addAppender(appender);
        }

        private void clear() {
            appender.list.clear();
        }

        private List<ILoggingEvent> snapshot() {
            return new ArrayList<ILoggingEvent>(appender.list);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
            appender.stop();
        }
    }
}
