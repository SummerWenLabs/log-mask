package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.MethodMetadata;
import org.springframework.web.client.RestTemplate;

/** Installs one observation interceptor on each explicitly selected instance. */
final class RestTemplateObservationInstaller
        implements BeanPostProcessor, SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;
    private final RestTemplateObservationProperties properties;
    private final ObjectProvider<RestTemplateObservationConfigurer> configurerProvider;
    private final Map<RestTemplate, String> pendingConfigurerCandidates =
            new IdentityHashMap<RestTemplate, String>();
    private volatile List<RestTemplateObservationConfigurer> configurers =
            Collections.emptyList();
    private volatile boolean configurersResolved;

    RestTemplateObservationInstaller(
            ConfigurableListableBeanFactory beanFactory,
            RestTemplateObservationProperties properties,
            ObjectProvider<RestTemplateObservationConfigurer> configurers) {
        this.beanFactory = beanFactory;
        this.properties = properties;
        this.configurerProvider = configurers;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!properties.isEnabled() || !(bean instanceof RestTemplate)) {
            return bean;
        }
        RestTemplate restTemplate = (RestTemplate) bean;
        if (isConfiguredName(beanName) || isAnnotated(beanName)) {
            install(restTemplate);
        } else if (configurersResolved) {
            if (isSelectedByConfigurer(beanName, restTemplate)) {
                install(restTemplate);
            }
        } else {
            synchronized (pendingConfigurerCandidates) {
                pendingConfigurerCandidates.put(restTemplate, beanName);
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
        configurers = Collections.unmodifiableList(resolved);
        configurersResolved = true;
        applyConfigurersToPendingCandidates();
        applyConfigurersToExistingSingletons();
    }

    private void applyConfigurersToPendingCandidates() {
        synchronized (pendingConfigurerCandidates) {
            for (Map.Entry<RestTemplate, String> candidate
                    : pendingConfigurerCandidates.entrySet()) {
                if (isSelectedByConfigurer(candidate.getValue(), candidate.getKey())) {
                    install(candidate.getKey());
                }
            }
            pendingConfigurerCandidates.clear();
        }
    }

    private void validateConfiguredNames() {
        List<String> beanNames = properties.getObservedBeanNames();
        if (beanNames == null) {
            return;
        }
        for (String beanName : beanNames) {
            if (!beanFactory.containsBean(beanName)) {
                throw new IllegalStateException(
                        "Configured observed RestTemplate bean '" + beanName + "' does not exist");
            }
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !RestTemplate.class.isAssignableFrom(beanType)) {
                throw new IllegalStateException(
                        "Configured observed bean '" + beanName + "' is not a RestTemplate");
            }
        }
    }

    private void applyConfigurersToExistingSingletons() {
        String[] beanNames = beanFactory.getBeanNamesForType(RestTemplate.class, true, false);
        for (String beanName : beanNames) {
            Object singleton = beanFactory.getSingleton(beanName);
            if (singleton instanceof RestTemplate) {
                RestTemplate restTemplate = (RestTemplate) singleton;
                if (isConfiguredName(beanName)
                        || isAnnotated(beanName)
                        || isSelectedByConfigurer(beanName, restTemplate)) {
                    install(restTemplate);
                }
            }
        }
    }

    private boolean isSelectedByConfigurer(String beanName, RestTemplate restTemplate) {
        for (RestTemplateObservationConfigurer configurer : configurers) {
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

    private static void install(RestTemplate restTemplate) {
        for (Object interceptor : restTemplate.getInterceptors()) {
            if (interceptor instanceof BodylessExchangeLoggingInterceptor) {
                return;
            }
        }
        restTemplate.getInterceptors().add(0, new BodylessExchangeLoggingInterceptor());
    }
}
