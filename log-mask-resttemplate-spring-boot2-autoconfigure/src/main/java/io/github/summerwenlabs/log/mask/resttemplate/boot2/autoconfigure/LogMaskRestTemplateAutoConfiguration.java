package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Entry point reserved for RestTemplate observation auto-configuration.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestTemplate.class)
public class LogMaskRestTemplateAutoConfiguration {
}
