/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import io.github.summerwenlabs.log.mask.resttemplate.boot2.RestTemplateObservationConfigurer;
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * Configures explicit RestTemplate HTTP exchange observation for Spring Boot 2.
 *
 * <p>A default annotated {@link RestTemplate} is created only when the
 * application has none. Existing instances are modified only when selected by
 * annotation, configured bean name, or
 * {@link RestTemplateObservationConfigurer}.
 *
 * @author SummerWen
 * @since 0.1
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RestTemplate.class, RestTemplateBuilder.class})
@AutoConfigureAfter(RestTemplateAutoConfiguration.class)
public class LogMaskRestTemplateAutoConfiguration {

    @Bean
    @ObservedRestTemplate
    @ConditionalOnMissingBean(RestTemplate.class)
    /**
     * Create the default explicitly observed RestTemplate when none exists.
     * @param builder application-configured RestTemplate builder
     * @return a new RestTemplate using the application's builder customizations
     */
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
            ApplicationContext applicationContext,
            Environment environment,
            ObjectProvider<RestTemplateObservationConfigurer> configurers,
            ObjectProvider<MaskTypeDefinition> maskTypeDefinitions) {
        RestTemplateObservationProperties properties = Binder.get(environment)
                .bind(
                        "log-mask.logging.rest-template",
                        RestTemplateObservationProperties.class)
                .orElseGet(RestTemplateObservationProperties::new);
        LogMaskGovernanceProperties governance = Binder.get(environment)
                .bind("log-mask.governance", LogMaskGovernanceProperties.class)
                .orElseGet(LogMaskGovernanceProperties::new);
        RestTemplateObservationSettings settings = RestTemplateObservationSettings.create(
                properties,
                governance,
                maskTypeDefinitions);
        return new RestTemplateObservationInstaller(
                beanFactory,
                properties,
                settings,
                configurers,
                new RestTemplateAdapterStartupSummary(applicationContext.getId()));
    }
}
