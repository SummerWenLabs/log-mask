/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.starter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MavenDevelopmentArtifactsIT {

    private static final List<String> PUBLIC_JAR_MODULES = Arrays.asList(
            "log-mask-core",
            "log-mask-http-core",
            "log-mask-resttemplate-spring-boot3-autoconfigure",
            "log-mask-resttemplate-spring-boot3-starter");

    private final Path repositoryRoot = Paths.get(
            System.getProperty("repository.root"));
    private final String version = System.getProperty("artifact.version");

    @Test
    void publishedJarsExposeTheMavenProjectVersion() throws IOException {
        for (String module : PUBLIC_JAR_MODULES) {
            try (JarFile jar = new JarFile(jarPath(module, null).toFile())) {
                assertEquals(
                        version,
                        jar.getManifest().getMainAttributes()
                                .getValue("Implementation-Version"),
                        module + " Manifest version");
            }
        }
    }

    @Test
    void publishedModulesAttachUsableSourceArtifacts() throws IOException {
        assertJarEntry(
                repositoryRoot.resolve("target").resolve(
                        "log-mask-parent-" + version + "-sources.jar"),
                "META-INF/maven/io.github.summerwenlabs/log-mask-parent/pom.xml",
                "log-mask-parent:sources");
        assertJarEntry(
                "log-mask-core",
                "sources",
                "io/github/summerwenlabs/log/mask/LogMasker.java");
        assertJarEntry(
                "log-mask-http-core",
                "sources",
                "io/github/summerwenlabs/log/mask/http/exchange/HttpExchangeEvent.java");
        assertJarEntry(
                "log-mask-resttemplate-spring-boot3-autoconfigure",
                "sources",
                "io/github/summerwenlabs/log/mask/resttemplate/boot3/ObservedRestTemplate.java");
        assertJarEntry(
                "log-mask-resttemplate-spring-boot3-autoconfigure",
                "sources",
                "io/github/summerwenlabs/log/mask/resttemplate/boot3/autoconfigure/"
                        + "RestTemplateObservationProperties.java");
        assertJarEntry(
                "log-mask-resttemplate-spring-boot3-starter",
                "sources",
                "META-INF/maven/io.github.summerwenlabs/"
                        + "log-mask-resttemplate-spring-boot3-starter/pom.xml");
    }

    @Test
    void autoconfigureJarPublishesItsVersionAndConfigurationContract()
            throws IOException {
        try (JarFile jar = new JarFile(jarPath(
                "log-mask-resttemplate-spring-boot3-autoconfigure",
                null).toFile())) {
            requiredEntry(jar, "META-INF/log-mask-resttemplate-spring-boot3.marker");
            requiredEntry(jar, "META-INF/spring.factories");
            requiredEntry(
                    jar,
                    "META-INF/spring/org.springframework.boot.autoconfigure."
                            + "AutoConfiguration.imports");

            Properties buildProperties = new Properties();
            try (InputStream versionInput = jar.getInputStream(requiredEntry(
                    jar,
                    "META-INF/log-mask-resttemplate-adapter-build.properties"))) {
                buildProperties.load(versionInput);
            }
            assertEquals(version, buildProperties.getProperty("version"));

            JsonNode metadata;
            try (InputStream metadataInput = jar.getInputStream(requiredEntry(
                    jar,
                    "META-INF/spring-configuration-metadata.json"))) {
                metadata = new ObjectMapper().readTree(metadataInput);
            }
            assertEquals(documentedConfigurationProperties(), publishedPropertyNames(metadata));
        }
    }

    private void assertJarEntry(String module, String classifier, String entryName)
            throws IOException {
        assertJarEntry(jarPath(module, classifier), entryName, module + ":" + classifier);
    }

    private void assertJarEntry(Path artifact, String entryName, String description)
            throws IOException {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertNotNull(jar.getJarEntry(entryName), description + " entry " + entryName);
        }
    }

    private JarEntry requiredEntry(JarFile jar, String entryName) {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        return entry;
    }

    private Set<String> publishedPropertyNames(JsonNode metadata) {
        Set<String> names = new HashSet<>();
        Iterator<JsonNode> properties = metadata.path("properties").elements();
        while (properties.hasNext()) {
            String name = properties.next().path("name").asText();
            if (name.startsWith("log-mask.")) {
                names.add(name);
            }
        }
        return names;
    }

    private Set<String> documentedConfigurationProperties() {
        return new HashSet<>(Arrays.asList(
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
                "log-mask.logging.rest-template.trace-id.mdc-keys"));
    }

    private Path jarPath(String module, String classifier) {
        String suffix = classifier == null ? "" : "-" + classifier;
        return repositoryRoot.resolve(module).resolve("target")
                .resolve(module + "-" + version + suffix + ".jar");
    }
}
