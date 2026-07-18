/* SPDX-License-Identifier: Apache-2.0 */

package example;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class ExpectedGenerationFailureProbe {

    private static boolean contextInitialized;

    private ExpectedGenerationFailureProbe() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Expected Spring Boot version and exact diagnostic message");
        }
        assertSpringBootVersion(args[0]);
        Throwable startupFailure = captureStartupFailure();
        if (contextInitialized) {
            throw new AssertionError(
                    "Generation verification ran after ApplicationContext initialization");
        }
        assertDiagnostic(startupFailure, args[1]);
    }

    private static void assertSpringBootVersion(String expectedVersion) {
        String actualVersion = SpringBootVersion.getVersion();
        if (!expectedVersion.equals(actualVersion)) {
            throw new AssertionError("Expected Spring Boot " + expectedVersion
                    + " but resolved " + actualVersion);
        }
    }

    private static Throwable captureStartupFailure() {
        try {
            ConfigurableApplicationContext context = new SpringApplicationBuilder(
                    ProbeApplication.class)
                    .web(WebApplicationType.NONE)
                    .initializers(applicationContext -> contextInitialized = true)
                    .run();
            context.close();
            throw new AssertionError("Application startup unexpectedly succeeded");
        }
        catch (AssertionError failure) {
            throw failure;
        }
        catch (Throwable failure) {
            return failure;
        }
    }

    private static void assertDiagnostic(Throwable failure, String expectedMessage) {
        Deque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        boolean diagnosticFound = false;
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current instanceof LinkageError || current instanceof ClassNotFoundException) {
                throw new AssertionError(
                        "Generation verification lost the race to incompatible linkage",
                        current);
            }
            if (current instanceof IllegalStateException
                    && expectedMessage.equals(current.getMessage())) {
                diagnosticFound = true;
            }
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            Collections.addAll(pending, current.getSuppressed());
        }
        if (!diagnosticFound) {
            throw new AssertionError(
                    "Expected generation diagnostic was not found: " + expectedMessage,
                    failure);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ProbeApplication {
    }
}
