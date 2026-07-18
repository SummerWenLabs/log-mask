/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootGenerationVerifierTest {

    @Test
    void acceptsSpringBoot3WhenOnlyTheMatchingAdapterIsPresent() {
        assertDoesNotThrow(() -> SpringBootGenerationVerifier.verify("3.5.16", false));
    }

    @Test
    void rejectsSpringBoot2BeforeLoadingTheBoot3Adapter() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> SpringBootGenerationVerifier.verify("2.7.18", false));

        assertTrue(failure.getMessage().contains("Spring Boot 2.7.18"));
        assertTrue(failure.getMessage().contains(
                "log-mask-resttemplate-spring-boot2-starter"));
    }

    @Test
    void rejectsBothAdapterGenerationsOnTheSameClasspath() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> SpringBootGenerationVerifier.verify("3.5.16", true));

        assertEquals(
                "Log Mask found both RestTemplate starter generations with Spring Boot "
                        + "3.5.16. Keep only io.github.summerwenlabs:"
                        + "log-mask-resttemplate-spring-boot3-starter and remove "
                        + "io.github.summerwenlabs:"
                        + "log-mask-resttemplate-spring-boot2-starter.",
                failure.getMessage());
    }
}
