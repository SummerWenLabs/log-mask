/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Rejects incompatible Spring Boot and RestTemplate adapter generations before
 * the auto-configuration can load Spring Framework 5-specific implementation.
 *
 * <p>This verifier is registered independently from auto-configuration because
 * a wrong-generation failure must precede linkage of the adapter itself.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class SpringBootGenerationVerifier implements EnvironmentPostProcessor {

    private static final String BOOT2_STARTER =
            "io.github.summerwenlabs:log-mask-resttemplate-spring-boot2-starter";
    private static final String BOOT3_STARTER =
            "io.github.summerwenlabs:log-mask-resttemplate-spring-boot3-starter";
    private static final String BOOT3_MARKER =
            "META-INF/log-mask-resttemplate-spring-boot3.marker";

    /**
     * Create the verifier used by Spring Boot's environment preparation phase.
     */
    public SpringBootGenerationVerifier() {
    }

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application) {
        ClassLoader classLoader = SpringBootGenerationVerifier.class.getClassLoader();
        verify(
                SpringBootVersion.getVersion(),
                classLoader.getResource(BOOT3_MARKER) != null);
    }

    static void verify(String springBootVersion, boolean boot3AdapterPresent) {
        if (boot3AdapterPresent) {
            String matchingStarter = springBootVersion != null
                    && springBootVersion.startsWith("3.")
                            ? BOOT3_STARTER : BOOT2_STARTER;
            String incompatibleStarter = BOOT2_STARTER.equals(matchingStarter)
                    ? BOOT3_STARTER : BOOT2_STARTER;
            throw new IllegalStateException(
                    "Log Mask found both RestTemplate starter generations with Spring Boot "
                            + springBootVersion + ". Keep only " + matchingStarter
                            + " and remove " + incompatibleStarter + ".");
        }
        if (springBootVersion != null && !springBootVersion.startsWith("2.")) {
            throw new IllegalStateException(
                    "Log Mask RestTemplate Spring Boot 2 starter requires Spring Boot 2.x, "
                            + "but detected Spring Boot " + springBootVersion + ". Replace "
                            + BOOT2_STARTER + " with " + BOOT3_STARTER + ".");
        }
    }
}
