/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.RestTemplateObservationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.MethodMetadata;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * Installs observation components on each explicitly selected RestTemplate.
 *
 * <p>Selection covers configured names and aliases, the marker annotation, and
 * Java configurers. Installation is idempotent by instance: supported
 * converters are decorated in place and one interceptor is inserted first,
 * without replacing application transport or other RestTemplate settings.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RestTemplateObservationInstaller
        implements BeanPostProcessor, SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;
    private final RestTemplateObservationProperties properties;
    private final RestTemplateObservationSettings settings;
    private final ObjectProvider<RestTemplateObservationConfigurer> configurerProvider;
    private final RestTemplateAdapterStartupSummary startupSummary;
    private final RestTemplateConfigurerPhase configurerPhase =
            new RestTemplateConfigurerPhase();
    /*
     * Startup installs and sealing take this monitor before a RestTemplate
     * monitor. The fixed order makes install-and-record atomic with sealing.
     */
    private final Object startupObservationMonitor = new Object();
    private Map<RestTemplate, Set<String>> startupObservations =
            new IdentityHashMap<RestTemplate, Set<String>>();
    private volatile RestTemplateObservationSnapshot startupObservationSnapshot =
            RestTemplateObservationSnapshot.empty();

    RestTemplateObservationInstaller(
            ConfigurableListableBeanFactory beanFactory,
            RestTemplateObservationProperties properties,
            RestTemplateObservationSettings settings,
            ObjectProvider<RestTemplateObservationConfigurer> configurers,
            RestTemplateAdapterStartupSummary startupSummary) {
        this.beanFactory = beanFactory;
        this.properties = properties;
        this.settings = settings;
        this.configurerProvider = configurers;
        this.startupSummary = startupSummary;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!properties.isEnabled() || !(bean instanceof RestTemplate)) {
            return bean;
        }
        RestTemplate restTemplate = (RestTemplate) bean;
        if (isConfiguredName(beanName) || isAnnotated(beanName)) {
            install(beanName, restTemplate);
        } else {
            List<RestTemplateObservationConfigurer> resolvedConfigurers =
                    configurerPhase.submit(beanName, restTemplate);
            if (resolvedConfigurers != null
                    && isSelectedByConfigurer(
                            resolvedConfigurers,
                            beanName,
                            restTemplate)) {
                install(beanName, restTemplate);
            }
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isEnabled()) {
            return;
        }
        validateConfiguredNames();
        List<RestTemplateObservationConfigurer> resolved =
                new ArrayList<RestTemplateObservationConfigurer>();
        for (RestTemplateObservationConfigurer configurer : configurerProvider) {
            resolved.add(configurer);
        }
        List<RestTemplateObservationConfigurer> resolvedConfigurers =
                Collections.unmodifiableList(resolved);
        List<RestTemplateConfigurerPhase.Candidate> pendingCandidates =
                configurerPhase.resolve(resolvedConfigurers);
        applyConfigurersToPendingCandidates(
                resolvedConfigurers,
                pendingCandidates);
        applyConfigurersToExistingSingletons(resolvedConfigurers);
        startupObservationSnapshot = sealStartupObservationSnapshot();
        startupSummary.publish(startupObservationSnapshot);
    }

    RestTemplateObservationSnapshot getStartupObservationSnapshot() {
        return startupObservationSnapshot;
    }

    private void applyConfigurersToPendingCandidates(
            List<RestTemplateObservationConfigurer> resolvedConfigurers,
            List<RestTemplateConfigurerPhase.Candidate> pendingCandidates) {
        for (RestTemplateConfigurerPhase.Candidate candidate : pendingCandidates) {
            if (isSelectedByConfigurer(
                    resolvedConfigurers,
                    candidate.getBeanName(),
                    candidate.getRestTemplate())) {
                install(candidate.getBeanName(), candidate.getRestTemplate());
            }
        }
    }

    private void validateConfiguredNames() {
        List<String> beanNames = properties.getObservedBeanNames();
        if (beanNames == null) {
            return;
        }
        for (String beanName : beanNames) {
            if (!beanFactory.containsLocalBean(beanName)) {
                throw new IllegalStateException(
                        "Configured observed RestTemplate bean '"
                                + beanName
                                + "' must be defined in the local ApplicationContext");
            }
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !RestTemplate.class.isAssignableFrom(beanType)) {
                throw new IllegalStateException(
                        "Configured observed bean '" + beanName + "' is not a RestTemplate");
            }
        }
    }

    private void applyConfigurersToExistingSingletons(
            List<RestTemplateObservationConfigurer> resolvedConfigurers) {
        String[] beanNames = beanFactory.getBeanNamesForType(RestTemplate.class, true, false);
        for (String beanName : beanNames) {
            Object singleton = beanFactory.getSingleton(beanName);
            if (singleton instanceof RestTemplate) {
                RestTemplate restTemplate = (RestTemplate) singleton;
                if (isConfiguredName(beanName)
                        || isAnnotated(beanName)
                        || isSelectedByConfigurer(
                                resolvedConfigurers,
                                beanName,
                                restTemplate)) {
                    install(beanName, restTemplate);
                }
            }
        }
    }

    private boolean isSelectedByConfigurer(
            List<RestTemplateObservationConfigurer> resolvedConfigurers,
            String beanName,
            RestTemplate restTemplate) {
        for (RestTemplateObservationConfigurer configurer : resolvedConfigurers) {
            if (configurer.shouldObserve(beanName, restTemplate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isConfiguredName(String beanName) {
        for (String configuredName : properties.getObservedBeanNames()) {
            if (configuredName.equals(beanName)) {
                return true;
            }
            for (String alias : beanFactory.getAliases(beanName)) {
                if (configuredName.equals(alias)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAnnotated(String beanName) {
        if (beanFactory.findAnnotationOnBean(beanName, ObservedRestTemplate.class) != null) {
            return true;
        }
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return false;
        }
        BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
        if (definition instanceof AnnotatedBeanDefinition) {
            MethodMetadata methodMetadata =
                    ((AnnotatedBeanDefinition) definition).getFactoryMethodMetadata();
            return methodMetadata != null
                    && methodMetadata.isAnnotated(ObservedRestTemplate.class.getName());
        }
        return false;
    }

    InstallationResult install(RestTemplate restTemplate) {
        synchronized (restTemplate) {
            return installLocked(restTemplate);
        }
    }

    private InstallationResult install(String beanName, RestTemplate restTemplate) {
        synchronized (startupObservationMonitor) {
            if (startupObservations != null) {
                synchronized (restTemplate) {
                    InstallationResult result = installLocked(restTemplate);
                    recordStartupObservationLocked(beanName, restTemplate);
                    return result;
                }
            }
        }
        return install(restTemplate);
    }

    private InstallationResult installLocked(RestTemplate restTemplate) {
        if (hasObservationChainLocked(restTemplate)) {
            return InstallationResult.ALREADY_OBSERVED;
        }
        RestTemplateObservationRuntime runtime =
                new RestTemplateObservationRuntime(settings);
        decorateSupportedConverters(restTemplate, runtime);
        restTemplate.getInterceptors().add(0, new ExchangeLoggingInterceptor(runtime));
        return InstallationResult.INSTALLED;
    }

    private RestTemplateObservationSnapshot sealStartupObservationSnapshot() {
        Map<RestTemplate, Set<String>> sealedObservations;
        synchronized (startupObservationMonitor) {
            String[] beanNames = beanFactory.getBeanNamesForType(
                    RestTemplate.class,
                    true,
                    false);
            for (String beanName : beanNames) {
                Object singleton = beanFactory.getSingleton(beanName);
                if (singleton instanceof RestTemplate) {
                    RestTemplate restTemplate = (RestTemplate) singleton;
                    synchronized (restTemplate) {
                        if (hasObservationChainLocked(restTemplate)) {
                            recordStartupObservationLocked(beanName, restTemplate);
                        }
                    }
                }
            }
            sealedObservations = startupObservations;
            startupObservations = null;
        }
        Set<String> observedBeanNames = new HashSet<String>();
        for (Set<String> beanNamesForInstance : sealedObservations.values()) {
            observedBeanNames.addAll(beanNamesForInstance);
        }
        List<String> sortedBeanNames = new ArrayList<String>(observedBeanNames);
        Collections.sort(sortedBeanNames);
        int observedInstanceCount = sealedObservations.size();
        sealedObservations.clear();
        return new RestTemplateObservationSnapshot(
                observedInstanceCount,
                sortedBeanNames);
    }

    private void recordStartupObservationLocked(
            String beanName,
            RestTemplate restTemplate) {
        Set<String> beanNames = startupObservations.get(restTemplate);
        if (beanNames == null) {
            beanNames = new HashSet<String>();
            startupObservations.put(restTemplate, beanNames);
        }
        beanNames.add(beanName);
    }

    private static boolean hasObservationChainLocked(RestTemplate restTemplate) {
        for (Object interceptor : restTemplate.getInterceptors()) {
            if (interceptor instanceof ExchangeLoggingInterceptor) {
                return true;
            }
        }
        return false;
    }

    private static void decorateSupportedConverters(
            RestTemplate restTemplate,
            RestTemplateObservationRuntime runtime) {
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        for (int index = 0; index < converters.size(); index++) {
            HttpMessageConverter<?> converter = converters.get(index);
            if (converter instanceof AbstractJackson2HttpMessageConverter) {
                converters.set(
                        index,
                        new ObservedJacksonHttpMessageConverter(
                                (AbstractJackson2HttpMessageConverter) converter,
                                runtime));
            } else if (converter instanceof ByteArrayHttpMessageConverter) {
                converters.set(
                        index,
                        new ObservedByteArrayHttpMessageConverter(
                                (ByteArrayHttpMessageConverter) converter,
                                runtime));
            } else if (converter instanceof StringHttpMessageConverter) {
                converters.set(
                        index,
                        new ObservedStringHttpMessageConverter(
                                (StringHttpMessageConverter) converter,
                                runtime));
            }
        }
    }

    enum InstallationResult {
        INSTALLED,
        ALREADY_OBSERVED
    }
}
