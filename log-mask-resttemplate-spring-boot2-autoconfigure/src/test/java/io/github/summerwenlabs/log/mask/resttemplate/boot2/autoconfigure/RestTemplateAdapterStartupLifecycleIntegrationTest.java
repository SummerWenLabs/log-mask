/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestTemplateAdapterStartupLifecycleIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class));
    private CapturedRestTemplateAdapterStartupLogs logs;

    @BeforeEach
    void captureEvents() {
        logs = new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO);
    }

    @AfterEach
    void releaseEvents() {
        logs.close();
    }

    @Test
    void successfulContextPublishesOneStartupSummaryWithoutPollutingHttpEvents() {
        contextRunner
                .withInitializer(context -> context.setId("selected-context"))
                .withUserConfiguration(SelectedTemplate.class)
                .run(context -> {
                    List<ILoggingEvent> summaries = logs.summaryEvents();
                    assertEquals(1, summaries.size());
                    assertEquals(
                            "Log Mask RestTemplate adapter initialized: "
                                    + "version=0.1.0-SNAPSHOT, "
                                    + "contextId=selected-context, "
                                    + "observedInstanceCountAtStartup=1, "
                                    + "observedBeanNamesAtStartup=[selected], "
                                    + "exchangeEventsEnabledAtStartup=true",
                            summaries.get(0).getFormattedMessage());
                    assertTrue(logs.exchangeEventLoggerEvents().isEmpty());

                    RestTemplate selected = context.getBean("selected", RestTemplate.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(selected).build();
                    server.expect(once(), requestTo("https://api.example.com/startup-summary"))
                            .andRespond(withStatus(HttpStatus.NO_CONTENT));

                    selected.getForEntity(
                            "https://api.example.com/startup-summary",
                            Void.class);

                    assertEquals(1, logs.exchangeEventLoggerEvents().size());
                    assertTrue(logs.exchangeEventLoggerEvents().get(0)
                            .getFormattedMessage().startsWith("{"));
                    assertEquals(1, logs.summaryEvents().size());
                });
    }

    @Test
    void successfulContextPublishesStartupSummaryWhenNoTemplateIsObserved() {
        contextRunner
                .withInitializer(context -> context.setId("zero-observed-context"))
                .withUserConfiguration(UnselectedTemplate.class)
                .run(context -> {
                    List<ILoggingEvent> summaries = logs.summaryEvents();

                    assertEquals(1, summaries.size());
                    assertEquals(
                            "Log Mask RestTemplate adapter initialized: "
                                    + "version=0.1.0-SNAPSHOT, "
                                    + "contextId=zero-observed-context, "
                                    + "observedInstanceCountAtStartup=0, "
                                    + "observedBeanNamesAtStartup=[], "
                                    + "exchangeEventsEnabledAtStartup=true",
                            summaries.get(0).getFormattedMessage());
                });
    }

    @Test
    void exchangeLoggerStateIsAStartupSnapshot() {
        logs.close();
        logs = new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.WARN);
        contextRunner
                .withInitializer(context -> context.setId("exchange-disabled-context"))
                .withUserConfiguration(SelectedTemplate.class)
                .run(context -> {
                    assertEquals(1, logs.summaryEvents().size());
                    assertTrue(logs.summaryEvents().get(0).getFormattedMessage()
                            .endsWith("exchangeEventsEnabledAtStartup=false"));
                    assertTrue(logs.exchangeEventLoggerEvents().isEmpty());

                    Logger exchangeLogger = (Logger) LoggerFactory.getLogger("log.mask.http");
                    exchangeLogger.setLevel(Level.INFO);
                    RestTemplate selected = context.getBean("selected", RestTemplate.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(selected).build();
                    server.expect(once(), requestTo("https://api.example.com/dynamic-level"))
                            .andRespond(withStatus(HttpStatus.NO_CONTENT));

                    selected.getForEntity(
                            "https://api.example.com/dynamic-level",
                            Void.class);

                    assertEquals(1, logs.exchangeEventLoggerEvents().size());
                    assertEquals(1, logs.summaryEvents().size());
                    assertTrue(logs.summaryEvents().get(0).getFormattedMessage()
                            .endsWith("exchangeEventsEnabledAtStartup=false"));
                });
    }

    @Test
    void lateLazyAndPrototypeTemplatesDoNotChangeStartupSummary() {
        logs.close();
        logs = new CapturedRestTemplateAdapterStartupLogs(Level.DEBUG, Level.INFO);
        contextRunner
                .withInitializer(context -> context.setId("late-template-context"))
                .withUserConfiguration(LateTemplates.class)
                .run(context -> {
                    AtomicInteger lazyCreations = context.getBean(AtomicInteger.class);
                    assertEquals(0, lazyCreations.get());
                    assertEquals(1, logs.summaryEvents().size());
                    assertEquals(
                            "Log Mask RestTemplate adapter initialized: "
                                    + "version=0.1.0-SNAPSHOT, "
                                    + "contextId=late-template-context, "
                                    + "observedInstanceCountAtStartup=0, "
                                    + "observedBeanNamesAtStartup=[], "
                                    + "exchangeEventsEnabledAtStartup=true",
                            logs.summaryEvents().get(0).getFormattedMessage());

                    RestTemplate lazy = context.getBean("lateLazy", RestTemplate.class);
                    RestTemplate firstPrototype = context.getBean(
                            "latePrototype",
                            RestTemplate.class);
                    RestTemplate secondPrototype = context.getBean(
                            "latePrototype",
                            RestTemplate.class);

                    assertEquals(1, lazyCreations.get());
                    assertEquals(1, observationInterceptorCount(lazy));
                    assertEquals(1, observationInterceptorCount(firstPrototype));
                    assertEquals(1, observationInterceptorCount(secondPrototype));
                    assertEquals(1, logs.summaryEvents().size());
                    assertEquals(Level.INFO, logs.summaryEvents().get(0).getLevel());
                });
    }

    private static long observationInterceptorCount(RestTemplate restTemplate) {
        return restTemplate.getInterceptors().stream()
                .filter(ExchangeLoggingInterceptor.class::isInstance)
                .count();
    }

    @Configuration(proxyBeanMethods = false)
    static class SelectedTemplate {

        @Bean
        @ObservedRestTemplate
        RestTemplate selected(RestTemplateBuilder builder) {
            return builder.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnselectedTemplate {

        @Bean
        RestTemplate unselected() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LateTemplates {

        @Bean
        AtomicInteger lazyCreations() {
            return new AtomicInteger();
        }

        @Bean
        RestTemplate eagerUnselected() {
            return new RestTemplate();
        }

        @Bean
        @Lazy
        @ObservedRestTemplate
        RestTemplate lateLazy(AtomicInteger lazyCreations) {
            lazyCreations.incrementAndGet();
            return new RestTemplate();
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        @ObservedRestTemplate
        RestTemplate latePrototype() {
            return new RestTemplate();
        }
    }

}
