/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.RestTemplateObservationConfigurer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class RestTemplateSelectionAutoConfigurationTest {

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
    void onlyAnnotatedRestTemplateIsObserved() {
        contextRunner.withUserConfiguration(SelectedTemplates.class).run(context -> {
            RestTemplate selected = context.getBean("selected", RestTemplate.class);
            RestTemplate untouched = context.getBean("untouched", RestTemplate.class);
            MockRestServiceServer selectedServer = MockRestServiceServer.bindTo(selected).build();
            MockRestServiceServer untouchedServer = MockRestServiceServer.bindTo(untouched).build();
            selectedServer.expect(once(), requestTo("https://api.example.com/selected"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));
            untouchedServer.expect(once(), requestTo("https://api.example.com/untouched"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            selected.getForEntity("https://api.example.com/selected", Void.class);
            untouched.getForEntity("https://api.example.com/untouched", Void.class);

            selectedServer.verify();
            untouchedServer.verify();
        });

        assertEquals(1, events.getEvents().size());
    }

    @Test
    void beanNameSelectionObservesOnlyNamedInstance() {
        contextRunner
                .withPropertyValues("log-mask.logging.rest-template.observed-bean-names=byName")
                .withUserConfiguration(NamedTemplate.class)
                .run(context -> {
                    RestTemplate selected = context.getBean("byName", RestTemplate.class);
                    RestTemplate untouched = context.getBean("untouched", RestTemplate.class);
                    MockRestServiceServer selectedServer = MockRestServiceServer.bindTo(selected).build();
                    MockRestServiceServer untouchedServer = MockRestServiceServer.bindTo(untouched).build();
                    selectedServer.expect(once(), requestTo("https://api.example.com/by-name"))
                            .andRespond(withStatus(HttpStatus.NO_CONTENT));
                    untouchedServer.expect(once(), requestTo("https://api.example.com/not-named"))
                            .andRespond(withStatus(HttpStatus.NO_CONTENT));

                    selected.getForEntity("https://api.example.com/by-name", Void.class);
                    untouched.getForEntity("https://api.example.com/not-named", Void.class);
                });

        assertEquals(1, events.getEvents().size());
    }

    @Test
    void javaConfigurerSelectionObservesMatchingInstance() {
        contextRunner.withUserConfiguration(ConfiguredTemplate.class).run(context -> {
            RestTemplate selected = context.getBean("configured", RestTemplate.class);
            RestTemplate untouched = context.getBean("untouched", RestTemplate.class);
            MockRestServiceServer selectedServer = MockRestServiceServer.bindTo(selected).build();
            MockRestServiceServer untouchedServer = MockRestServiceServer.bindTo(untouched).build();
            selectedServer.expect(once(), requestTo("https://api.example.com/configured"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));
            untouchedServer.expect(once(), requestTo("https://api.example.com/not-configured"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            selected.getForEntity("https://api.example.com/configured", Void.class);
            untouched.getForEntity("https://api.example.com/not-configured", Void.class);
        });

        assertEquals(1, events.getEvents().size());
    }

    @Test
    void duplicateSelectionByIdentityInstallsOneObserver() {
        contextRunner
                .withPropertyValues("log-mask.logging.rest-template.observed-bean-names=shared")
                .withUserConfiguration(DuplicateSelection.class)
                .run(context -> {
                    RestTemplate shared = context.getBean("shared", RestTemplate.class);
                    MockRestServiceServer server = MockRestServiceServer.bindTo(shared).build();
                    server.expect(once(), requestTo("https://api.example.com/shared"))
                            .andRespond(withStatus(HttpStatus.NO_CONTENT));

                    shared.getForEntity("https://api.example.com/shared", Void.class);
                });

        assertEquals(1, events.getEvents().size());
    }

    @Test
    void invalidObservedBeanNamePreventsStartup() {
        contextRunner
                .withPropertyValues("log-mask.logging.rest-template.observed-bean-names=missing")
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure);
                    assertTrue(startupFailure.getMessage().contains("missing"));
                });
    }

    @Test
    void invalidObservedBeanTypePreventsStartup() {
        contextRunner
                .withPropertyValues("log-mask.logging.rest-template.observed-bean-names=wrong")
                .withUserConfiguration(WrongObservedType.class)
                .run(context -> {
                    Throwable startupFailure = context.getStartupFailure();
                    assertNotNull(startupFailure);
                    assertTrue(startupFailure.getMessage().contains("wrong"));
                    assertTrue(startupFailure.getMessage().contains("RestTemplate"));
                });
    }

    @Test
    void disabledLoggingDoesNotObserveSelectedTemplate() {
        contextRunner
                .withPropertyValues("log-mask.logging.rest-template.enabled=false")
                .withUserConfiguration(SelectedTemplates.class)
                .run(context -> {
                    assertTrue(context.getBeansOfType(
                            RestTemplateObservationInstaller.class).isEmpty());
                    RestTemplate selected = context.getBean("selected", RestTemplate.class);
                    assertFalse(selected.getInterceptors().stream()
                            .anyMatch(ExchangeLoggingInterceptor.class::isInstance));
                    MockRestServiceServer server = MockRestServiceServer.bindTo(selected).build();
                    server.expect(once(), requestTo("https://api.example.com/disabled"))
                            .andRespond(withStatus(HttpStatus.NO_CONTENT));

                    selected.getForEntity("https://api.example.com/disabled", Void.class);
                });

        assertEquals(0, events.getEvents().size());
    }

    @Test
    void disabledLoggingDoesNotValidateObservedBeanNames() {
        contextRunner
                .withPropertyValues(
                        "log-mask.logging.rest-template.enabled=false",
                        "log-mask.logging.rest-template.observed-bean-names=missing")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void disabledInfoLoggerBypassesObservationBeforeRequestSnapshot() {
        events.disableInfo();
        contextRunner.withUserConfiguration(SelectedTemplates.class).run(context -> {
            RestTemplate selected = context.getBean("selected", RestTemplate.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(selected).build();
            server.expect(once(), requestTo("https://api.example.com/info-off"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            selected.getForEntity("https://api.example.com/info-off", Void.class);
        });

        assertEquals(0, events.getEvents().size());
    }

    @Test
    void unselectedLazyRestTemplateIsNotCreatedAtStartup() {
        contextRunner.withUserConfiguration(LazyUnselectedTemplate.class).run(context -> {
            AtomicInteger creations = context.getBean(AtomicInteger.class);
            assertEquals(0, creations.get());

            RestTemplate lazy = context.getBean("lazyUnselected", RestTemplate.class);

            assertEquals(1, creations.get());
            assertTrue(lazy.getInterceptors().isEmpty());
        });
    }

    @Test
    void annotatedPrototypeIsObservedForEveryCreatedInstance() {
        contextRunner.withUserConfiguration(PrototypeTemplate.class).run(context -> {
            RestTemplate first = context.getBean("prototype", RestTemplate.class);
            RestTemplate second = context.getBean("prototype", RestTemplate.class);
            MockRestServiceServer firstServer = MockRestServiceServer.bindTo(first).build();
            MockRestServiceServer secondServer = MockRestServiceServer.bindTo(second).build();
            firstServer.expect(once(), requestTo("https://api.example.com/prototype/1"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));
            secondServer.expect(once(), requestTo("https://api.example.com/prototype/2"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            first.getForEntity("https://api.example.com/prototype/1", Void.class);
            second.getForEntity("https://api.example.com/prototype/2", Void.class);
        });

        assertEquals(2, events.getEvents().size());
    }

    @Test
    void adapterDoesNotRegisterGlobalRestTemplateCustomizer() {
        contextRunner.run(context -> assertTrue(
                context.getBeansOfType(RestTemplateCustomizer.class).isEmpty()));
    }

    @Test
    void preRegisteredSingletonCanBeSelectedByBeanName() {
        RestTemplate registered = new RestTemplate();
        contextRunner
                .withInitializer(context -> context.getBeanFactory()
                        .registerSingleton("registered", registered))
                .withPropertyValues(
                        "log-mask.logging.rest-template.observed-bean-names=registered")
                .run(context -> {
                    assertSame(registered, context.getBean("registered"));
                    MockRestServiceServer server = MockRestServiceServer.bindTo(registered).build();
                    server.expect(once(), requestTo("https://api.example.com/registered"))
                            .andRespond(withStatus(HttpStatus.NO_CONTENT));

                    registered.getForEntity("https://api.example.com/registered", Void.class);
                });

        assertEquals(1, events.getEvents().size());
    }

    @Test
    void configurerSelectsPrototypeCreatedDuringSingletonInitialization() {
        contextRunner.withUserConfiguration(EarlyPrototypeTemplate.class).run(context -> {
            RestTemplate prototype = context.getBean(PrototypeHolder.class).restTemplate;
            MockRestServiceServer server = MockRestServiceServer.bindTo(prototype).build();
            server.expect(once(), requestTo("https://api.example.com/early-prototype"))
                    .andRespond(withStatus(HttpStatus.NO_CONTENT));

            prototype.getForEntity("https://api.example.com/early-prototype", Void.class);
        });

        assertEquals(1, events.getEvents().size());
    }

    @Configuration(proxyBeanMethods = false)
    static class SelectedTemplates {

        @Bean
        @ObservedRestTemplate
        RestTemplate selected(RestTemplateBuilder builder) {
            return builder.build();
        }

        @Bean
        RestTemplate untouched() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NamedTemplate {

        @Bean
        RestTemplate byName() {
            return new RestTemplate();
        }

        @Bean
        RestTemplate untouched() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConfiguredTemplate {

        @Bean
        RestTemplate configured() {
            return new RestTemplate();
        }

        @Bean
        RestTemplate untouched() {
            return new RestTemplate();
        }

        @Bean
        RestTemplateObservationConfigurer configurer() {
            return (beanName, restTemplate) -> "configured".equals(beanName);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateSelection {

        @Bean
        @ObservedRestTemplate
        RestTemplate shared() {
            return new RestTemplate();
        }

        @Bean
        RestTemplateObservationConfigurer configurer() {
            return (beanName, restTemplate) -> "shared".equals(beanName);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class WrongObservedType {

        @Bean
        String wrong() {
            return "not a RestTemplate";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LazyUnselectedTemplate {

        @Bean
        AtomicInteger lazyCreations() {
            return new AtomicInteger();
        }

        @Bean
        @Lazy
        RestTemplate lazyUnselected(AtomicInteger lazyCreations) {
            lazyCreations.incrementAndGet();
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrototypeTemplate {

        @Bean
        @ObservedRestTemplate
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        RestTemplate prototype() {
            return new RestTemplate();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EarlyPrototypeTemplate {

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        RestTemplate configuredPrototype() {
            return new RestTemplate();
        }

        @Bean
        PrototypeHolder prototypeHolder(
                @Qualifier("configuredPrototype") RestTemplate restTemplate) {
            return new PrototypeHolder(restTemplate);
        }

        @Bean
        RestTemplateObservationConfigurer prototypeConfigurer() {
            return (beanName, restTemplate) -> "configuredPrototype".equals(beanName);
        }
    }

    static final class PrototypeHolder {
        private final RestTemplate restTemplate;

        private PrototypeHolder(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }
    }

}
