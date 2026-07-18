/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.LogMaskRestTemplateAutoConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskRestTemplateAutoConfigurationDiscoveryTest {

    @Test
    void registersAutoConfigurationThroughBoot3Imports() throws IOException {
        String resourceName = "META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        InputStream resource = getClass().getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(resource);
        List<String> autoConfigurationNames;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource,
                StandardCharsets.UTF_8))) {
            autoConfigurationNames = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        }

        assertTrue(autoConfigurationNames.contains(LogMaskRestTemplateAutoConfiguration.class.getName()));
    }
}
