/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationMetadataIntegrationTest {

    private static final String METADATA_RESOURCE =
            "META-INF/spring-configuration-metadata.json";

    @Test
    void publishesOnlyDocumentedConfigurationProperties() throws IOException {
        Set<String> propertyNames = publishedPropertyNames();

        assertEquals(new HashSet<String>(Arrays.asList(
                "log-mask.governance.enabled",
                "log-mask.governance.http.path.rules",
                "log-mask.governance.http.query.rules",
                "log-mask.governance.http.headers.request.rules",
                "log-mask.governance.http.headers.response.rules",
                "log-mask.logging.rest-template.enabled",
                "log-mask.logging.rest-template.observed-bean-names",
                "log-mask.logging.rest-template.uri.details-enabled",
                "log-mask.logging.rest-template.name-value-shape",
                "log-mask.logging.rest-template.max-body-size",
                "log-mask.logging.rest-template.request.headers-enabled",
                "log-mask.logging.rest-template.request.body-enabled",
                "log-mask.logging.rest-template.response.headers-enabled",
                "log-mask.logging.rest-template.response.body-enabled",
                "log-mask.logging.rest-template.trace-id.enabled",
                "log-mask.logging.rest-template.trace-id.mdc-keys")), propertyNames);
    }

    private Set<String> publishedPropertyNames() throws IOException {
        Set<String> names = new HashSet<String>();
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources(METADATA_RESOURCE);
        while (resources.hasMoreElements()) {
            InputStream input = resources.nextElement().openStream();
            try {
                JsonNode metadata = new ObjectMapper().readTree(input);
                Iterator<JsonNode> properties = metadata.path("properties").elements();
                while (properties.hasNext()) {
                    String name = properties.next().path("name").asText();
                    if (name.startsWith("log-mask.")) {
                        names.add(name);
                    }
                }
            }
            finally {
                input.close();
            }
        }
        return names;
    }
}
