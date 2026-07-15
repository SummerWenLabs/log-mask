package io.github.summerwenlabs.log.mask.samples;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class LogMaskSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogMaskSampleApplication.class, args);
    }

    @Bean
    @Profile("!selection-demo & !request-only-demo")
    @ConditionalOnProperty(
            prefix = "log-mask.samples.demo",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    ApplicationRunner defaultDemoRunner(DefaultSampleClient client) {
        return arguments -> {
            client.typedJson();
            client.stringBody();
            client.byteArrayBody();
            client.noBody();
        };
    }

    @Bean
    @Profile("selection-demo")
    @ConditionalOnProperty(
            prefix = "log-mask.samples.demo",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    ApplicationRunner selectionDemoRunner(SelectionSampleClient client) {
        return arguments -> {
            client.annotated();
            client.byName();
            client.programmatic();
            client.shared();
        };
    }

}
