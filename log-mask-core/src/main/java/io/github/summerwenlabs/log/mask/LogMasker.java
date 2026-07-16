package io.github.summerwenlabs.log.mask;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Generates thread-safe JSON representations intended only for logging.
 *
 * <p>The supplied {@link ObjectMapper} is copied. Governance annotations and
 * custom strategies apply only to that copy and never change application JSON
 * serialization. Serialization failures are reported as
 * {@link LogMaskException}.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class LogMasker {

    private final ObjectMapper objectMapper;
    private final ObjectWriter writer;

    private LogMasker(
            ObjectMapper objectMapper,
            MaskStrategyRegistry strategyRegistry,
            boolean governanceEnabled) {
        ObjectMapper safeObjectMapper = objectMapper.copy();
        safeObjectMapper.disable(SerializationFeature.CLOSE_CLOSEABLE);
        if (governanceEnabled) {
            MaskingDiagnostics diagnostics = new MaskingDiagnostics();
            safeObjectMapper.setSerializerFactory(
                    safeObjectMapper.getSerializerFactory()
                            .withSerializerModifier(
                                    new SafeObjectBeanSerializerModifier(
                                            strategyRegistry,
                                            diagnostics)));
        }
        this.objectMapper = safeObjectMapper;
        this.writer = safeObjectMapper.writer();
    }

    /**
     * Create a builder from an application Jackson configuration.
     * @param objectMapper mapper to copy; never mutated by the resulting masker
     * @return a new builder
     * @throws NullPointerException if {@code objectMapper} is {@code null}
     */
    public static Builder builder(ObjectMapper objectMapper) {
        return new Builder(objectMapper);
    }

    /**
     * Generate a complete safe JSON representation without a byte budget.
     * @param value value to represent; {@code null} produces JSON {@code null}
     * @return the complete JSON document
     * @throws LogMaskException if the value cannot be serialized
     */
    public String mask(Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new LogMaskException("Unable to generate a safe object representation", exception);
        }
    }

    /**
     * Generate a complete safe JSON representation within a UTF-8 byte limit.
     * @param value value to represent
     * @param maxUtf8Bytes maximum permitted size of the final JSON, in UTF-8
     * bytes
     * @return a complete JSON result, or a limit-exceeded result without
     * partial JSON
     * @throws IllegalArgumentException if {@code maxUtf8Bytes} is less than one
     * @throws LogMaskException if the value cannot be serialized
     */
    public BoundedMaskResult mask(Object value, int maxUtf8Bytes) {
        return mask(value, writer, maxUtf8Bytes);
    }

    /**
     * Generate a complete safe JSON representation using a declared Java type.
     * @param value value to represent
     * @param declaredType declared type used by the caller
     * @param maxUtf8Bytes maximum permitted size of the final JSON, in UTF-8
     * bytes
     * @return a complete JSON result, or a limit-exceeded result without
     * partial JSON
     * @throws NullPointerException if {@code declaredType} is {@code null}
     * @throws IllegalArgumentException if {@code maxUtf8Bytes} is less than one
     * @throws LogMaskException if the value cannot be serialized
     */
    public BoundedMaskResult mask(
            Object value,
            Type declaredType,
            int maxUtf8Bytes) {
        Objects.requireNonNull(declaredType, "declaredType");
        ObjectWriter typedWriter = objectMapper.writerFor(
                objectMapper.getTypeFactory().constructType(declaredType));
        return mask(value, typedWriter, maxUtf8Bytes);
    }

    private BoundedMaskResult mask(
            Object value,
            ObjectWriter valueWriter,
            int maxUtf8Bytes) {
        if (maxUtf8Bytes < 1) {
            throw new IllegalArgumentException("maxUtf8Bytes must be at least 1");
        }
        BoundedUtf8OutputStream output = new BoundedUtf8OutputStream(maxUtf8Bytes);
        BoundedJsonGenerator generator = null;
        Throwable failure = null;
        try {
            generator = new BoundedJsonGenerator(valueWriter.createGenerator(output), output);
            valueWriter.writeValue(generator, value);
            generator.flush();
            return BoundedMaskResult.complete(output.toUtf8String());
        } catch (IOException | RuntimeException exception) {
            failure = exception;
            if (output.isLimitExceeded()) {
                return BoundedMaskResult.limitExceeded();
            }
            throw new LogMaskException("Unable to generate a safe object representation", exception);
        } finally {
            closeGenerator(generator, output, failure);
        }
    }

    private void closeGenerator(
            BoundedJsonGenerator generator,
            BoundedUtf8OutputStream output,
            Throwable failure) {
        if (generator == null) {
            return;
        }
        output.discardFurtherWrites();
        generator.disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT);
        try {
            generator.close();
        } catch (IOException | RuntimeException closeFailure) {
            if (failure != null) {
                failure.addSuppressed(closeFailure);
                return;
            }
            throw new LogMaskException("Unable to generate a safe object representation", closeFailure);
        }
    }

    /**
     * Configures an immutable {@link LogMasker} from a copied mapper.
     */
    public static final class Builder {
        private final ObjectMapper objectMapper;
        private MaskStrategyRegistry strategyRegistry = MaskStrategyRegistry.empty();
        private boolean governanceEnabled = true;

        private Builder(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        }

        /**
         * Use the registry for exact, application-defined mask type codes.
         * @param strategyRegistry immutable strategy registry
         * @return this builder
         * @throws NullPointerException if {@code strategyRegistry} is
         * {@code null}
         */
        public Builder strategyRegistry(MaskStrategyRegistry strategyRegistry) {
            this.strategyRegistry = Objects.requireNonNull(strategyRegistry, "strategyRegistry");
            return this;
        }

        /**
         * Set whether governance annotations are applied to safe JSON.
         * @param governanceEnabled {@code true} to apply explicit field rules
         * @return this builder
         */
        public Builder governanceEnabled(boolean governanceEnabled) {
            this.governanceEnabled = governanceEnabled;
            return this;
        }

        /**
         * Build a thread-safe masker with the current settings.
         * @return a new masker backed by an isolated mapper copy
         */
        public LogMasker build() {
            return new LogMasker(objectMapper, strategyRegistry, governanceEnabled);
        }
    }
}
