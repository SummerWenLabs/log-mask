/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.integration;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TypedJsonBodyObservationIntegrationTest {

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
    void typedRequestAndResponseAreGovernedWithoutChangingWireOrBusinessValues() {
        GovernedPayload request = new GovernedPayload(
                "request", "13800138000", "request-internal");
        contextRunner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/typed"))
                    .andExpect(content().json(
                            "{\"label\":\"request\",\"phone\":\"13800138000\","
                                    + "\"internal\":\"request-internal\"}"))
                    .andRespond(withSuccess(
                            "{\"label\":\"response\",\"phone\":\"13900139000\","
                                    + "\"internal\":\"response-internal\"}",
                            MediaType.APPLICATION_JSON));

            ResponseEntity<GovernedPayload> response = restTemplate.exchange(
                    "https://api.example.com/typed",
                    HttpMethod.POST,
                    new HttpEntity<GovernedPayload>(request),
                    GovernedPayload.class);

            server.verify();
            assertNotNull(response.getBody());
            assertEquals("13900139000", response.getBody().getPhone());
            assertEquals("response-internal", response.getBody().getInternal());
        });

        assertEquals("13800138000", request.getPhone());
        assertEquals("request-internal", request.getInternal());
        assertEquals(1, events.getEvents().size());
        JsonNode event = readEvent(events.getEvents().get(0).getFormattedMessage());
        assertEquals("SUCCESS", event.path("request").path("bodyState").textValue());
        assertGovernedBody(event.path("request").path("body"), "request", "138****8000");
        assertEquals("SUCCESS", event.path("response").path("bodyState").textValue());
        assertGovernedBody(event.path("response").path("body"), "response", "139****9000");
    }

    @Test
    void declaredGenericTypesGovernCollectionsAndMaps() {
        ExtendedGovernedPayload requestPayload = new ExtendedGovernedPayload(
                "request", "13800138000", "request-internal", "subtype-only");
        Map<String, List<GovernedPayload>> request =
                new LinkedHashMap<String, List<GovernedPayload>>();
        request.put("items", Collections.<GovernedPayload>singletonList(requestPayload));
        Type requestType = new ParameterizedTypeReference<
                Map<String, List<GovernedPayload>>>() {
        }.getType();
        ParameterizedTypeReference<Map<String, GovernedPayload>> responseType =
                new ParameterizedTypeReference<Map<String, GovernedPayload>>() {
                };

        contextRunner.withUserConfiguration(StaticTypingConfiguration.class).run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(once(), requestTo("https://api.example.com/generic"))
                    .andExpect(content().json(
                            "{\"items\":[{\"label\":\"request\","
                                    + "\"phone\":\"13800138000\","
                                    + "\"internal\":\"request-internal\"}]}"))
                    .andRespond(withSuccess(
                            "{\"primary\":{\"label\":\"response\","
                                    + "\"phone\":\"13900139000\","
                                    + "\"internal\":\"response-internal\","
                                    + "\"wireUnknown\":\"wire-only\"}}",
                            MediaType.APPLICATION_JSON));
            RequestEntity<Map<String, List<GovernedPayload>>> requestEntity =
                    RequestEntity.post(URI.create("https://api.example.com/generic"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request, requestType);

            ResponseEntity<Map<String, GovernedPayload>> response =
                    restTemplate.exchange(requestEntity, responseType);

            server.verify();
            GovernedPayload responsePayload = response.getBody().get("primary");
            assertEquals("13900139000", responsePayload.getPhone());
            assertEquals("response-internal", responsePayload.getInternal());
        });

        assertEquals("13800138000", requestPayload.getPhone());
        assertEquals("request-internal", requestPayload.getInternal());
        assertEquals(1, events.getEvents().size());
        JsonNode event = readEvent(events.getEvents().get(0).getFormattedMessage());
        JsonNode requestBody = event.path("request").path("body")
                .path("items").path(0);
        assertGovernedBody(
                requestBody,
                "request",
                "138****8000");
        assertFalse(requestBody.has("subtypeOnly"));
        JsonNode responseBody = event.path("response").path("body").path("primary");
        assertGovernedBody(
                responseBody,
                "response",
                "139****9000");
        assertFalse(responseBody.has("wireUnknown"));
    }

    @Test
    void disabledGovernanceRecordsRawTypedBodiesAndDeclaresTheEventState() {
        GovernedPayload request = new GovernedPayload(
                "request", "13800138000", "request-internal");
        contextRunner.withPropertyValues("log-mask.governance.enabled=false")
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    MockRestServiceServer server =
                            MockRestServiceServer.bindTo(restTemplate).build();
                    server.expect(once(), requestTo("https://api.example.com/raw"))
                            .andExpect(content().json(
                                    "{\"label\":\"request\","
                                            + "\"phone\":\"13800138000\","
                                            + "\"internal\":\"request-internal\"}"))
                            .andRespond(withSuccess(
                                    "{\"label\":\"response\","
                                            + "\"phone\":\"13900139000\","
                                            + "\"internal\":\"response-internal\"}",
                                    MediaType.APPLICATION_JSON));

                    ResponseEntity<GovernedPayload> response = restTemplate.exchange(
                            "https://api.example.com/raw",
                            HttpMethod.POST,
                            new HttpEntity<GovernedPayload>(request),
                            GovernedPayload.class);

                    server.verify();
                    assertEquals("13900139000", response.getBody().getPhone());
                    assertEquals("response-internal", response.getBody().getInternal());
                });

        assertEquals(1, events.getEvents().size());
        JsonNode event = readEvent(events.getEvents().get(0).getFormattedMessage());
        assertFalse(event.path("governanceEnabled").booleanValue());
        assertRawBody(
                event.path("request").path("body"),
                "request",
                "13800138000",
                "request-internal");
        assertRawBody(
                event.path("response").path("body"),
                "response",
                "13900139000",
                "response-internal");
    }

    @Test
    void typeSpecificObjectMapperShapesWireAndLogViewsConsistently() {
        NamedPayload request = new NamedPayload("request name", "13800138000");
        contextRunner.withUserConfiguration(TypeSpecificMapperConfiguration.class)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    MockRestServiceServer server =
                            MockRestServiceServer.bindTo(restTemplate).build();
                    server.expect(once(), requestTo("https://api.example.com/type-mapper"))
                            .andExpect(content().json(
                                    "{\"display_name\":\"request name\","
                                            + "\"phone\":\"13800138000\"}"))
                            .andRespond(withSuccess(
                                    "{\"display_name\":\"response name\","
                                            + "\"phone\":\"13900139000\"}",
                                    MediaType.APPLICATION_JSON));

                    ResponseEntity<NamedPayload> response = restTemplate.exchange(
                            "https://api.example.com/type-mapper",
                            HttpMethod.POST,
                            new HttpEntity<NamedPayload>(request),
                            NamedPayload.class);

                    server.verify();
                    assertEquals("response name", response.getBody().getDisplayName());
                    assertEquals("13900139000", response.getBody().getPhone());
                });

        assertEquals(1, events.getEvents().size());
        JsonNode event = readEvent(events.getEvents().get(0).getFormattedMessage());
        assertNamedBody(
                event.path("request").path("body"),
                "request name",
                "138****8000");
        assertNamedBody(
                event.path("response").path("body"),
                "response name",
                "139****9000");
    }

    private static void assertGovernedBody(JsonNode body, String label, String phone) {
        assertEquals(label, body.path("label").textValue());
        assertEquals(phone, body.path("phone").textValue());
        assertFalse(body.has("internal"));
    }

    private static void assertRawBody(
            JsonNode body,
            String label,
            String phone,
            String internal) {
        assertEquals(label, body.path("label").textValue());
        assertEquals(phone, body.path("phone").textValue());
        assertEquals(internal, body.path("internal").textValue());
    }

    private static void assertNamedBody(JsonNode body, String name, String phone) {
        assertEquals(name, body.path("display_name").textValue());
        assertFalse(body.has("displayName"));
        assertEquals(phone, body.path("phone").textValue());
    }

    private JsonNode readEvent(String message) {
        try {
            return objectMapper.readTree(message);
        } catch (Exception exception) {
            throw new AssertionError("logger message must be valid JSON", exception);
        }
    }

    static class GovernedPayload {
        private String label;
        private String phone;
        private String internal;

        GovernedPayload() {
        }

        GovernedPayload(String label, String phone, String internal) {
            this.label = label;
            this.phone = phone;
            this.internal = internal;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        @Mask(type = MaskType.PHONE)
        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        @LogExclude
        public String getInternal() {
            return internal;
        }

        public void setInternal(String internal) {
            this.internal = internal;
        }
    }

    static final class ExtendedGovernedPayload extends GovernedPayload {
        private final String subtypeOnly;

        ExtendedGovernedPayload(
                String label,
                String phone,
                String internal,
                String subtypeOnly) {
            super(label, phone, internal);
            this.subtypeOnly = subtypeOnly;
        }

        public String getSubtypeOnly() {
            return subtypeOnly;
        }
    }

    @JsonDeserialize(as = NamedPayloadSubtype.class)
    static class NamedPayload {
        private String displayName;
        private String phone;

        NamedPayload() {
        }

        NamedPayload(String displayName, String phone) {
            this.displayName = displayName;
            this.phone = phone;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        @Mask(type = MaskType.PHONE)
        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    static final class NamedPayloadSubtype extends NamedPayload {
        NamedPayloadSubtype() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StaticTypingConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate staticTypingRestTemplate() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(MapperFeature.USE_STATIC_TYPING);
            mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            MappingJackson2HttpMessageConverter converter =
                    new MappingJackson2HttpMessageConverter(mapper);
            return new RestTemplate(Collections.<HttpMessageConverter<?>>singletonList(converter));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TypeSpecificMapperConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate typeSpecificMapperRestTemplate() {
            MappingJackson2HttpMessageConverter converter =
                    new MappingJackson2HttpMessageConverter(new ObjectMapper());
            ObjectMapper subtypeMapper = new ObjectMapper();
            converter.registerObjectMappersForType(
                    NamedPayloadSubtype.class,
                    mappers -> mappers.put(MediaType.APPLICATION_JSON, subtypeMapper));
            ObjectMapper typeMapper = new ObjectMapper();
            typeMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            converter.registerObjectMappersForType(
                    NamedPayload.class,
                    mappers -> mappers.put(MediaType.APPLICATION_JSON, typeMapper));
            return new RestTemplate(Collections.<HttpMessageConverter<?>>singletonList(converter));
        }
    }

}
