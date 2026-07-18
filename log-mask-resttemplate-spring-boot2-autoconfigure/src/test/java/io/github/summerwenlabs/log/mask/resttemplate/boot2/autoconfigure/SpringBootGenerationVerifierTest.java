/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootGenerationVerifierTest {

    @Test
    void acceptsSpringBoot2WhenOnlyTheMatchingAdapterIsPresent() {
        assertDoesNotThrow(() -> SpringBootGenerationVerifier.verify("2.7.18", false));
    }

    @Test
    void rejectsSpringBoot3BeforeLoadingTheBoot2Adapter() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> SpringBootGenerationVerifier.verify("3.5.16", false));

        assertTrue(failure.getMessage().contains("Spring Boot 3.5.16"));
        assertTrue(failure.getMessage().contains(
                "log-mask-resttemplate-spring-boot3-starter"));
    }

    @Test
    void rejectsBothAdapterGenerationsOnTheSameClasspath() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> SpringBootGenerationVerifier.verify("2.7.18", true));

        assertEquals(
                "Log Mask found both RestTemplate starter generations with Spring Boot "
                        + "2.7.18. Keep only io.github.summerwenlabs:"
                        + "log-mask-resttemplate-spring-boot2-starter and remove "
                        + "io.github.summerwenlabs:"
                        + "log-mask-resttemplate-spring-boot3-starter.",
                failure.getMessage());
    }
}
