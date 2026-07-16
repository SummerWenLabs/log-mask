package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.integration;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.CapturedHttpEvents;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure.LogMaskRestTemplateAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorResponseBodyObservationIntegrationTest {

    private static final String ERROR_PREFIX = "visible-error-prefix";
    private static final String ERROR_SUFFIX = "|suffix-must-remain-unread";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class))
            .withUserConfiguration(ControlledErrorResponseConfiguration.class);
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
    void errorHandlerExceptionIsUnchangedAndOnlyItsConsumedPrefixIsLogged() {
        byte[] responseBody = utf8(ERROR_PREFIX + ERROR_SUFFIX);

        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);
            PrefixReadingErrorHandler errorHandler =
                    context.getBean(PrefixReadingErrorHandler.class);
            transport.prepare(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    responseBody,
                    Long.valueOf(responseBody.length),
                    "error-handler");

            RestClientException thrown = assertThrows(
                    RestClientException.class,
                    () -> restTemplate.getForObject(
                            "https://api.example.com/error-prefix",
                            String.class));

            assertSame(errorHandler.getFailure(), thrown);
            assertArrayEquals(utf8(ERROR_PREFIX), errorHandler.getConsumedPrefix());
        });

        String formattedMessage = singleFormattedMessage();
        JsonNode response = readEvent(formattedMessage).path("response");
        assertEquals(422, response.path("status").intValue());
        assertEquals("SUCCESS", response.path("headersState").textValue());
        assertEquals(
                Arrays.asList("error-handler"),
                valuesFor(response.path("headers"), "x-response-marker"));
        assertSuccessfulTextBody(response, ERROR_PREFIX);
        assertFalse(formattedMessage.contains(ERROR_SUFFIX));
    }

    @Test
    void confirmedEmptyResponsesAreNullButAnUnreadPossibleBodyIsAnEmptyString() {
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            ControlledResponseFactory transport =
                    context.getBean(ControlledResponseFactory.class);

            transport.prepare(
                    HttpStatus.NO_CONTENT,
                    new byte[0],
                    null,
                    "status-204");
            String statusResult = restTemplate.execute(
                    "https://api.example.com/status-204",
                    HttpMethod.GET,
                    null,
                    response -> "status-result");

            transport.prepare(
                    HttpStatus.OK,
                    new byte[0],
                    Long.valueOf(0L),
                    "content-length-zero");
            String lengthResult = restTemplate.execute(
                    "https://api.example.com/content-length-zero",
                    HttpMethod.GET,
                    null,
                    response -> "length-result");

            transport.prepare(
                    HttpStatus.OK,
                    new byte[0],
                    null,
                    "empty-stream-eof");
            int endOfInput = restTemplate.execute(
                    "https://api.example.com/empty-stream-eof",
                    HttpMethod.GET,
                    null,
                    response -> response.getBody().read());

            transport.prepare(
                    HttpStatus.OK,
                    utf8("available but deliberately unread"),
                    null,
                    "possible-unread-body");
            String unreadResult = restTemplate.execute(
                    "https://api.example.com/possible-unread-body",
                    HttpMethod.GET,
                    null,
                    response -> "unread-result");

            assertEquals("status-result", statusResult);
            assertEquals("length-result", lengthResult);
            assertEquals(-1, endOfInput);
            assertEquals("unread-result", unreadResult);
        });

        assertEquals(4, events.getEvents().size());
        assertNullBody(readEvent(0).path("response"), 204);
        assertNullBody(readEvent(1).path("response"), 200);
        assertNullBody(readEvent(2).path("response"), 200);
        assertSuccessfulTextBody(readEvent(3).path("response"), "");
    }

    private void assertNullBody(JsonNode response, int expectedStatus) {
        assertEquals(expectedStatus, response.path("status").intValue());
        assertEquals("SUCCESS", response.path("bodyState").textValue());
        assertTrue(response.path("body").isNull());
    }

    private void assertSuccessfulTextBody(JsonNode response, String expected) {
        assertEquals("SUCCESS", response.path("bodyState").textValue());
        assertTrue(response.path("body").isTextual());
        assertEquals(expected, response.path("body").textValue());
    }

    private JsonNode readEvent(int index) {
        return readEvent(events.getEvents().get(index).getFormattedMessage());
    }

    private String singleFormattedMessage() {
        assertEquals(1, events.getEvents().size());
        return events.getEvents().get(0).getFormattedMessage();
    }

    private JsonNode readEvent(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    private static List<String> valuesFor(JsonNode entries, String name) {
        for (JsonNode entry : entries) {
            if (name.equals(entry.path("name").textValue())) {
                String[] values = new String[entry.path("values").size()];
                for (int index = 0; index < values.length; index++) {
                    JsonNode value = entry.path("values").get(index);
                    values[index] = value.isNull() ? null : value.textValue();
                }
                return Arrays.asList(values);
            }
        }
        throw new AssertionError("missing name/value entry: " + name);
    }

    private static byte[] readExactly(
            java.io.InputStream input,
            int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read < 0) {
                throw new IOException("error response ended before the expected prefix");
            }
            offset += read;
        }
        return result;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Configuration(proxyBeanMethods = false)
    static class ControlledErrorResponseConfiguration {

        @Bean
        ControlledResponseFactory controlledResponseFactory() {
            return new ControlledResponseFactory();
        }

        @Bean
        PrefixReadingErrorHandler prefixReadingErrorHandler() {
            return new PrefixReadingErrorHandler();
        }

        @Bean
        @ObservedRestTemplate
        RestTemplate controlledErrorResponseRestTemplate(
                ControlledResponseFactory requestFactory,
                PrefixReadingErrorHandler errorHandler) {
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            restTemplate.setErrorHandler(errorHandler);
            return restTemplate;
        }
    }

    static final class PrefixReadingErrorHandler implements ResponseErrorHandler {
        private final RestClientException failure =
                new RestClientException("expected error-handler failure");
        private byte[] consumedPrefix;

        @Override
        public boolean hasError(ClientHttpResponse response) throws IOException {
            return response.getRawStatusCode() >= 400;
        }

        @Override
        public void handleError(ClientHttpResponse response) throws IOException {
            consumedPrefix = readExactly(response.getBody(), utf8(ERROR_PREFIX).length);
            throw failure;
        }

        RestClientException getFailure() {
            return failure;
        }

        byte[] getConsumedPrefix() {
            return consumedPrefix;
        }
    }

    static final class ControlledResponseFactory implements ClientHttpRequestFactory {
        private HttpStatus nextStatus;
        private byte[] nextBody;
        private Long nextContentLength;
        private String nextMarker;

        void prepare(
                HttpStatus status,
                byte[] body,
                Long contentLength,
                String marker) {
            nextStatus = status;
            nextBody = body.clone();
            nextContentLength = contentLength;
            nextMarker = marker;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
            if (nextStatus == null || nextBody == null) {
                throw new IllegalStateException("response was not prepared");
            }
            HttpStatus status = nextStatus;
            byte[] body = nextBody.clone();
            Long contentLength = nextContentLength;
            String marker = nextMarker;
            return new MockClientHttpRequest(httpMethod, uri) {
                @Override
                protected ClientHttpResponse executeInternal() {
                    MockClientHttpResponse response =
                            new MockClientHttpResponse(body, status);
                    response.getHeaders().setContentType(new MediaType(
                            MediaType.TEXT_PLAIN.getType(),
                            MediaType.TEXT_PLAIN.getSubtype(),
                            StandardCharsets.UTF_8));
                    if (contentLength != null) {
                        response.getHeaders().setContentLength(contentLength.longValue());
                    }
                    response.getHeaders().add("X-Response-Marker", marker);
                    return response;
                }
            };
        }
    }

}
