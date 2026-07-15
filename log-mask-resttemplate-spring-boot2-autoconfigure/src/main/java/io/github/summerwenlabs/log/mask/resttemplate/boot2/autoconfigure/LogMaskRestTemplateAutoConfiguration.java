package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for the default observed RestTemplate.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RestTemplate.class, RestTemplateBuilder.class})
@AutoConfigureAfter(RestTemplateAutoConfiguration.class)
public class LogMaskRestTemplateAutoConfiguration {

    @Bean
    @ObservedRestTemplate
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate logMaskRestTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "log-mask.logging.rest-template",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static RestTemplateObservationInstaller restTemplateObservationInstaller(
            ConfigurableListableBeanFactory beanFactory,
            Environment environment,
            ObjectProvider<RestTemplateObservationConfigurer> configurers) {
        RestTemplateObservationProperties properties = Binder.get(environment)
                .bind(
                        "log-mask.logging.rest-template",
                        RestTemplateObservationProperties.class)
                .orElseGet(RestTemplateObservationProperties::new);
        boolean governanceEnabled = Binder.get(environment)
                .bind("log-mask.governance.enabled", Boolean.class)
                .orElse(Boolean.TRUE);
        return new RestTemplateObservationInstaller(
                beanFactory,
                properties,
                governanceEnabled,
                configurers);
    }
}
