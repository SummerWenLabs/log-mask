/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import io.github.summerwenlabs.log.mask.resttemplate.boot3.ObservedRestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestTemplateAdapterStartupFailureAndContextIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestTemplateAutoConfiguration.class,
                    LogMaskRestTemplateAutoConfiguration.class));

    @Test
    void installationFailureDoesNotPublishSuccessfulStartupSummary() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO)) {
            contextRunner
                    .withUserConfiguration(InstallationFailureConfiguration.class)
                    .run(context -> {
                        assertNotNull(context.getStartupFailure());
                        assertTrue(mostSpecificCause(context.getStartupFailure())
                                .getMessage()
                                .contains("forced RestTemplate installation failure"));
                        assertTrue(logs.summaryEvents().isEmpty());
                    });
        }
    }

    @Test
    void disabledAdapterDoesNotPublishStartupSummary() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO)) {
            contextRunner
                    .withPropertyValues("log-mask.logging.rest-template.enabled=false")
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertTrue(context.getBeansOfType(
                                RestTemplateObservationInstaller.class).isEmpty());
                        assertTrue(logs.summaryEvents().isEmpty());
                    });
        }
    }

    @Test
    void autoConfigurationConditionMismatchDoesNotPublishStartupSummary() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO)) {
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            LogMaskRestTemplateAutoConfiguration.class))
                    .withClassLoader(new FilteredClassLoader(RestTemplateBuilder.class))
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertTrue(logs.summaryEvents().isEmpty());
                        assertTrue(context.getBeansOfType(
                                RestTemplateObservationInstaller.class).isEmpty());
                    });
        }
    }

    @Test
    void configuredNameValidationFailureDoesNotPublishStartupSummary() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO)) {
            contextRunner
                    .withPropertyValues(
                            "log-mask.logging.rest-template.observed-bean-names=missing")
                    .run(context -> {
                        assertNotNull(context.getStartupFailure());
                        assertTrue(mostSpecificCause(context.getStartupFailure())
                                .getMessage()
                                .contains("missing"));
                        assertTrue(logs.summaryEvents().isEmpty());
                    });
        }
    }

    @Test
    void summaryInfoDisabledDoesNotPublishButContextStartsSuccessfully() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.WARN, Level.INFO)) {
            contextRunner
                    .withUserConfiguration(SelectedTemplateConfiguration.class)
                    .run(context -> {
                        assertNull(context.getStartupFailure());
                        assertTrue(logs.summaryEvents().isEmpty());
                        RestTemplate selected = context.getBean(
                                "selectedTemplate",
                                RestTemplate.class);
                        assertEquals(
                                1,
                                selected.getInterceptors().stream()
                                        .filter(ExchangeLoggingInterceptor.class::isInstance)
                                        .count());
                    });
        }
    }

    @Test
    void parentAndChildContextsPublishOnlyTheirLocalObservedTemplates() {
        try (CapturedRestTemplateAdapterStartupLogs logs =
                     new CapturedRestTemplateAdapterStartupLogs(Level.INFO, Level.INFO)) {
            contextRunner
                    .withInitializer(context -> context.setId("parent-context"))
                    .withUserConfiguration(ParentTemplateConfiguration.class)
                    .run(parent -> {
                        assertNull(parent.getStartupFailure());
                        RestTemplate parentTemplate = parent.getBean(
                                "parentTemplate",
                                RestTemplate.class);

                        contextRunner
                                .withParent(parent)
                                .withInitializer(context -> context.setId("child-context"))
                                .withUserConfiguration(ChildTemplateConfiguration.class)
                                .run(child -> {
                                    assertNull(child.getStartupFailure());
                                    RestTemplate childTemplate = child.getBean(
                                            "childTemplate",
                                            RestTemplate.class);
                                    assertSame(
                                            parentTemplate,
                                            child.getBean("parentTemplate", RestTemplate.class));
                                    assertEquals(
                                            1,
                                            parentTemplate.getInterceptors().stream()
                                                    .filter(ExchangeLoggingInterceptor.class::isInstance)
                                                    .count());
                                    assertEquals(
                                            1,
                                            childTemplate.getInterceptors().stream()
                                                    .filter(ExchangeLoggingInterceptor.class::isInstance)
                                                    .count());

                                    List<String> messages = logs.summaryEvents().stream()
                                            .map(event -> event.getFormattedMessage())
                                            .collect(Collectors.toList());
                                    assertEquals(2, messages.size());
                                    assertTrue(messages.contains(
                                            "Log Mask RestTemplate adapter initialized: "
                                                    + "version=0.1.0, "
                                                    + "contextId=parent-context, "
                                                    + "observedInstanceCountAtStartup=1, "
                                                    + "observedBeanNamesAtStartup="
                                                    + "[parentTemplate], "
                                                    + "exchangeEventsEnabledAtStartup=true"));
                                    assertTrue(messages.contains(
                                            "Log Mask RestTemplate adapter initialized: "
                                                    + "version=0.1.0, "
                                                    + "contextId=child-context, "
                                                    + "observedInstanceCountAtStartup=1, "
                                                    + "observedBeanNamesAtStartup="
                                                    + "[childTemplate], "
                                                    + "exchangeEventsEnabledAtStartup=true"));
                                });
                    });
        }
    }

    private static Throwable mostSpecificCause(Throwable failure) {
        Throwable result = failure;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    static class ParentTemplateConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate parentTemplate() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ChildTemplateConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate childTemplate() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SelectedTemplateConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate selectedTemplate() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InstallationFailureConfiguration {

        @Bean
        @ObservedRestTemplate
        RestTemplate installationFailureRestTemplate() {
            return new InstallationFailureRestTemplate();
        }
    }

    static final class InstallationFailureRestTemplate extends RestTemplate {

        @Override
        public List<ClientHttpRequestInterceptor> getInterceptors() {
            throw new IllegalStateException("forced RestTemplate installation failure");
        }
    }
}
