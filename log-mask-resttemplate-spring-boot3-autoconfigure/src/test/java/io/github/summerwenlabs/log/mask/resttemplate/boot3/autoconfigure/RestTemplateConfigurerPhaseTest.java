/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.summerwenlabs.log.mask.resttemplate.boot3.RestTemplateObservationConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestTemplateConfigurerPhaseTest {

    @Test
    void candidateBeforeTransitionIsEvaluatedAfterConfigurersResolve() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithConfigurer(
                (beanName, restTemplate) -> "before".equals(beanName));
        RestTemplate restTemplate = new RestTemplate();
        beanFactory.registerSingleton("before", restTemplate);
        RestTemplateObservationInstaller installer = installer(beanFactory);

        installer.postProcessAfterInitialization(restTemplate, "before");
        installer.afterSingletonsInstantiated();

        assertObserved(restTemplate);
    }

    @Test
    void candidateDuringConfigurerResolutionIsDrainedAfterTransition() throws Exception {
        CountDownLatch resolutionStarted = new CountDownLatch(1);
        CountDownLatch continueResolution = new CountDownLatch(1);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition configurerDefinition =
                new RootBeanDefinition(RestTemplateObservationConfigurer.class);
        configurerDefinition.setInstanceSupplier(() -> {
            resolutionStarted.countDown();
            await(continueResolution);
            return (RestTemplateObservationConfigurer) (beanName, restTemplate) ->
                    "during".equals(beanName);
        });
        beanFactory.registerBeanDefinition("blockingConfigurer", configurerDefinition);
        RestTemplate restTemplate = new RestTemplate();
        beanFactory.registerSingleton("during", restTemplate);
        RestTemplateObservationInstaller installer = installer(beanFactory);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> transition = executor.submit(installer::afterSingletonsInstantiated);
            assertTrue(resolutionStarted.await(5, TimeUnit.SECONDS));

            installer.postProcessAfterInitialization(restTemplate, "during");
            continueResolution.countDown();
            transition.get(5, TimeUnit.SECONDS);
        } finally {
            continueResolution.countDown();
            executor.shutdownNow();
        }

        assertObserved(restTemplate);
    }

    @Test
    void candidateAfterTransitionUsesResolvedConfigurersDirectly() {
        DefaultListableBeanFactory beanFactory = beanFactoryWithConfigurer(
                (beanName, restTemplate) -> "after".equals(beanName));
        RestTemplateObservationInstaller installer = installer(beanFactory);
        installer.afterSingletonsInstantiated();
        RestTemplateObservationSnapshot startupSnapshot =
                installer.getStartupObservationSnapshot();
        RestTemplate restTemplate = new RestTemplate();
        beanFactory.registerSingleton("after", restTemplate);

        installer.postProcessAfterInitialization(restTemplate, "after");

        assertObserved(restTemplate);
        assertSame(startupSnapshot, installer.getStartupObservationSnapshot());
        assertEquals(0, startupSnapshot.getObservedInstanceCountAtStartup());
        assertTrue(startupSnapshot.getObservedBeanNamesAtStartup().isEmpty());
    }

    @Test
    void configurerFailureStillEscapesStartupTransition() {
        IllegalStateException expected = new IllegalStateException("configurer failure");
        DefaultListableBeanFactory beanFactory = beanFactoryWithConfigurer(
                (beanName, restTemplate) -> {
                    throw expected;
                });
        RestTemplate restTemplate = new RestTemplate();
        beanFactory.registerSingleton("failing", restTemplate);
        RestTemplateObservationInstaller installer = installer(beanFactory);
        installer.postProcessAfterInitialization(restTemplate, "failing");

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                installer::afterSingletonsInstantiated);

        assertSame(expected, actual);
    }

    @Test
    void concurrentCandidateAndTransitionRouteCandidateExactlyOnce() throws Exception {
        RestTemplateObservationConfigurer configurer = (beanName, restTemplate) -> true;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int attempt = 0; attempt < 100; attempt++) {
                RestTemplateConfigurerPhase phase = new RestTemplateConfigurerPhase();
                RestTemplate restTemplate = new RestTemplate();
                CyclicBarrier start = new CyclicBarrier(2);

                Future<List<RestTemplateObservationConfigurer>> submission = executor.submit(() -> {
                    await(start);
                    return phase.submit("candidate", restTemplate);
                });
                Future<List<RestTemplateConfigurerPhase.Candidate>> transition =
                        executor.submit(() -> {
                            await(start);
                            return phase.resolve(Collections.singletonList(configurer));
                        });

                List<RestTemplateObservationConfigurer> directConfigurers =
                        submission.get(5, TimeUnit.SECONDS);
                List<RestTemplateConfigurerPhase.Candidate> drainedCandidates =
                        transition.get(5, TimeUnit.SECONDS);

                if (directConfigurers == null) {
                    assertEquals(1, drainedCandidates.size());
                    assertEquals("candidate", drainedCandidates.get(0).getBeanName());
                    assertSame(restTemplate, drainedCandidates.get(0).getRestTemplate());
                } else {
                    assertEquals(1, directConfigurers.size());
                    assertSame(configurer, directConfigurers.get(0));
                    assertTrue(drainedCandidates.isEmpty());
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test coordination");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static DefaultListableBeanFactory beanFactoryWithConfigurer(
            RestTemplateObservationConfigurer configurer) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("configurer", configurer);
        return beanFactory;
    }

    private static RestTemplateObservationInstaller installer(
            DefaultListableBeanFactory beanFactory) {
        return new RestTemplateObservationInstaller(
                beanFactory,
                new RestTemplateObservationProperties(),
                RestTemplateObservationSettings.defaults(true),
                beanFactory.getBeanProvider(RestTemplateObservationConfigurer.class),
                new RestTemplateAdapterStartupSummary("test-context"));
    }

    private static void assertObserved(RestTemplate restTemplate) {
        assertEquals(
                1,
                restTemplate.getInterceptors().stream()
                        .filter(ExchangeLoggingInterceptor.class::isInstance)
                        .count());
    }
}
