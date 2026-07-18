/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the RestTemplate adapter version from its package manifest or
 * filtered build metadata.
 *
 * @author SummerWen
 * @since 0.1
 */
final class RestTemplateAdapterVersion {

    private static final String BUILD_PROPERTIES =
            "META-INF/log-mask-resttemplate-adapter-build.properties";

    private RestTemplateAdapterVersion() {
    }

    static String get() {
        return get(RestTemplateAdapterVersion.class.getPackage(),
                RestTemplateAdapterVersion.class.getClassLoader());
    }

    static String get(Package adapterPackage, ClassLoader classLoader) {
        if (adapterPackage != null) {
            String implementationVersion = adapterPackage.getImplementationVersion();
            if (implementationVersion != null && !implementationVersion.trim().isEmpty()) {
                return implementationVersion.trim();
            }
        }
        InputStream input = classLoader.getResourceAsStream(BUILD_PROPERTIES);
        if (input == null) {
            return "unknown";
        }
        try {
            Properties properties = new Properties();
            properties.load(input);
            String version = properties.getProperty("version");
            return version == null || version.trim().isEmpty()
                    ? "unknown" : version.trim();
        }
        catch (IOException | IllegalArgumentException ex) {
            return "unknown";
        }
        finally {
            try {
                input.close();
            }
            catch (IOException ignored) {
                // Reading has already completed; a close failure cannot recover a version.
            }
        }
    }
}
