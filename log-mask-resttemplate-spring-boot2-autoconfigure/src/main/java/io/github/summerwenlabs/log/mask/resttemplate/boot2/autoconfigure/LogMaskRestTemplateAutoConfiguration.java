package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for the default observed RestTemplate.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({RestTemplate.class, RestTemplateBuilder.class})
@AutoConfigureAfter(RestTemplateAutoConfiguration.class)
public class LogMaskRestTemplateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RestTemplate.class)
    public RestTemplate logMaskRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder.build();
        restTemplate.getInterceptors().add(0, new BodylessExchangeLoggingInterceptor());
        return restTemplate;
    }
}
