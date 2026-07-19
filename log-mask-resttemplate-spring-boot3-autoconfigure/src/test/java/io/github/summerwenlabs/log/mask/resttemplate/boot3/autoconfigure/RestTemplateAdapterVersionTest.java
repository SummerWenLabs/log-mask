/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestTemplateAdapterVersionTest {

    @Test
    void reportsMavenVersionWhenRunningFromClassesDirectory() {
        assertEquals("0.1.0", RestTemplateAdapterVersion.get());
    }

    @Test
    void prefersManifestVersionOverBuildResource() {
        VersionClassLoader classLoader = new VersionClassLoader("resource-version");

        assertEquals("manifest-version", RestTemplateAdapterVersion.get(
                classLoader.defineVersionedPackage("manifest-version"), classLoader));
    }

    @Test
    void reportsUnknownWhenBothVersionSourcesAreUnavailable() {
        ClassLoader emptyClassLoader = new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return null;
            }
        };

        assertEquals("unknown", RestTemplateAdapterVersion.get(null, emptyClassLoader));
    }

    @Test
    void reportsUnknownWhenBuildResourceIsMalformed() {
        VersionClassLoader classLoader = new VersionClassLoader("\\uZZZZ");

        assertEquals("unknown", RestTemplateAdapterVersion.get(null, classLoader));
    }

    private static final class VersionClassLoader extends ClassLoader {

        private final String resourceVersion;

        private VersionClassLoader(String resourceVersion) {
            this.resourceVersion = resourceVersion;
        }

        private Package defineVersionedPackage(String implementationVersion) {
            return definePackage("example.versioned", null, null, null,
                    null, implementationVersion, null, null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return new ByteArrayInputStream(
                    ("version=" + resourceVersion).getBytes(StandardCharsets.UTF_8));
        }
    }
}
