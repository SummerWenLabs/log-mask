/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.starter;

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
            "log-mask-resttemplate-spring-boot2-autoconfigure",
            "log-mask-resttemplate-spring-boot2-starter");

    private final Path repositoryRoot = Paths.get(
            System.getProperty("repository.root"));
    private final String version = System.getProperty("artifact.version");

    @Test
    void publishedJarsExposeTheMavenProjectVersion() throws IOException {
        for (String module : PUBLIC_JAR_MODULES) {
            JarFile jar = new JarFile(jarPath(module, null).toFile());
            try {
                assertEquals(version,
                        jar.getManifest().getMainAttributes()
                                .getValue("Implementation-Version"),
                        module + " Manifest version");
            }
            finally {
                jar.close();
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
        assertJarEntry("log-mask-core", "sources",
                "io/github/summerwenlabs/log/mask/LogMasker.java");
        assertJarEntry("log-mask-http-core", "sources",
                "io/github/summerwenlabs/log/mask/http/exchange/HttpExchangeEvent.java");
        assertJarEntry("log-mask-resttemplate-spring-boot2-autoconfigure", "sources",
                "io/github/summerwenlabs/log/mask/resttemplate/boot2/ObservedRestTemplate.java");
        assertJarEntry("log-mask-resttemplate-spring-boot2-autoconfigure", "sources",
                "io/github/summerwenlabs/log/mask/resttemplate/boot2/autoconfigure/"
                        + "RestTemplateObservationProperties.java");
        assertJarEntry("log-mask-resttemplate-spring-boot2-starter", "sources",
                "META-INF/maven/io.github.summerwenlabs/"
                        + "log-mask-resttemplate-spring-boot2-starter/pom.xml");
    }

    @Test
    void autoconfigureJarPublishesItsVersionAndConfigurationContract()
            throws IOException {
        JarFile jar = new JarFile(jarPath(
                "log-mask-resttemplate-spring-boot2-autoconfigure", null).toFile());
        try {
            requiredEntry(jar, "META-INF/log-mask-resttemplate-spring-boot2.marker");
            requiredEntry(jar, "META-INF/spring.factories");

            Properties buildProperties = new Properties();
            InputStream versionInput = jar.getInputStream(requiredEntry(jar,
                    "META-INF/log-mask-resttemplate-adapter-build.properties"));
            try {
                buildProperties.load(versionInput);
            }
            finally {
                versionInput.close();
            }
            assertEquals(version, buildProperties.getProperty("version"));

            InputStream metadataInput = jar.getInputStream(requiredEntry(jar,
                    "META-INF/spring-configuration-metadata.json"));
            JsonNode metadata;
            try {
                metadata = new ObjectMapper().readTree(metadataInput);
            }
            finally {
                metadataInput.close();
            }
            assertEquals(documentedConfigurationProperties(),
                    publishedPropertyNames(metadata));
        }
        finally {
            jar.close();
        }
    }

    private void assertJarEntry(String module, String classifier, String entryName)
            throws IOException {
        assertJarEntry(
                jarPath(module, classifier),
                entryName,
                module + ":" + classifier);
    }

    private void assertJarEntry(Path artifact, String entryName, String description)
            throws IOException {
        JarFile jar = new JarFile(artifact.toFile());
        try {
            assertNotNull(jar.getJarEntry(entryName),
                    description + " entry " + entryName);
        }
        finally {
            jar.close();
        }
    }

    private JarEntry requiredEntry(JarFile jar, String entryName) {
        JarEntry entry = jar.getJarEntry(entryName);
        assertNotNull(entry, entryName);
        return entry;
    }

    private Set<String> publishedPropertyNames(JsonNode metadata) {
        Set<String> names = new HashSet<String>();
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
        return new HashSet<String>(Arrays.asList(
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
