/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.summerwenlabs.log.mask.resttemplate.boot2.RestTemplateObservationConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestTemplateObservationInstallerConcurrencyTest {

    @Test
    void installationResultDistinguishesNewAndExistingObservationChains() {
        RestTemplateObservationInstaller installer = installerFor("shared");
        RestTemplate restTemplate = new RestTemplate();

        assertSame(
                RestTemplateObservationInstaller.InstallationResult.INSTALLED,
                installer.install(restTemplate));
        assertSame(
                RestTemplateObservationInstaller.InstallationResult.ALREADY_OBSERVED,
                installer.install(restTemplate));
    }

    @Test
    void concurrentSelectionInstallsOneObservationChain() throws Exception {
        RestTemplateObservationInstaller installer = installerFor("shared");
        CoordinatedRestTemplate restTemplate = new CoordinatedRestTemplate();
        long supportedConverterCount = restTemplate.getMessageConverters().stream()
                .filter(RestTemplateObservationInstallerConcurrencyTest::isSupportedConverter)
                .count();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> process(
                    installer,
                    restTemplate,
                    ready,
                    start));
            Future<?> second = executor.submit(() -> process(
                    installer,
                    restTemplate,
                    ready,
                    start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(
                1,
                restTemplate.getInterceptors().stream()
                        .filter(ExchangeLoggingInterceptor.class::isInstance)
                        .count());
        assertEquals(
                supportedConverterCount,
                restTemplate.getMessageConverters().stream()
                        .filter(RestTemplateObservationInstallerConcurrencyTest::isObservedConverter)
                        .count());
    }

    private static void process(
            RestTemplateObservationInstaller installer,
            RestTemplate restTemplate,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        installer.postProcessAfterInitialization(restTemplate, "shared");
    }

    private static RestTemplateObservationInstaller installerFor(String beanName) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RestTemplateObservationProperties properties = new RestTemplateObservationProperties();
        properties.setObservedBeanNames(Collections.singletonList(beanName));
        return new RestTemplateObservationInstaller(
                beanFactory,
                properties,
                RestTemplateObservationSettings.defaults(true),
                beanFactory.getBeanProvider(RestTemplateObservationConfigurer.class),
                new RestTemplateAdapterStartupSummary("test-context"));
    }

    private static boolean isSupportedConverter(HttpMessageConverter<?> converter) {
        return converter instanceof AbstractJackson2HttpMessageConverter
                || converter instanceof ByteArrayHttpMessageConverter
                || converter instanceof StringHttpMessageConverter;
    }

    private static boolean isObservedConverter(HttpMessageConverter<?> converter) {
        return converter instanceof ObservedJacksonHttpMessageConverter
                || converter instanceof ObservedByteArrayHttpMessageConverter
                || converter instanceof ObservedStringHttpMessageConverter;
    }

    private static final class CoordinatedRestTemplate extends RestTemplate {

        private final AtomicInteger interceptorAccesses = new AtomicInteger();
        private final CountDownLatch initialChecks = new CountDownLatch(2);

        @Override
        public List<ClientHttpRequestInterceptor> getInterceptors() {
            if (interceptorAccesses.incrementAndGet() <= 2) {
                initialChecks.countDown();
                try {
                    // The fixed installer serializes checks, so its first check times out.
                    initialChecks.await(250, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            return super.getInterceptors();
        }
    }
}
