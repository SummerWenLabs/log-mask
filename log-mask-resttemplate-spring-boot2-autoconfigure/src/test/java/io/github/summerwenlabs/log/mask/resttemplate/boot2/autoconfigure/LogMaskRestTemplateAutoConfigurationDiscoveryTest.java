package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskRestTemplateAutoConfigurationDiscoveryTest {

    @Test
    void registersAutoConfigurationThroughSpringFactories() {
        List<String> autoConfigurationNames = SpringFactoriesLoader.loadFactoryNames(
                EnableAutoConfiguration.class,
                getClass().getClassLoader());

        assertTrue(autoConfigurationNames.contains(LogMaskRestTemplateAutoConfiguration.class.getName()));
    }
}
