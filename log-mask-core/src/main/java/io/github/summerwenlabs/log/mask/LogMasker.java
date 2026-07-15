package io.github.summerwenlabs.log.mask;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Thread-safe generator of JSON representations intended only for logging.
 */
public final class LogMasker {

    private final ObjectWriter writer;

    private LogMasker(ObjectMapper objectMapper) {
        ObjectMapper safeObjectMapper = objectMapper.copy();
        safeObjectMapper.setSerializerFactory(
                safeObjectMapper.getSerializerFactory()
                        .withSerializerModifier(new SafeObjectBeanSerializerModifier()));
        this.writer = safeObjectMapper.writer();
    }

    public static Builder builder(ObjectMapper objectMapper) {
        return new Builder(objectMapper);
    }

    public String mask(Object value) {
        try {
            return writer.writeValueAsString(value);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new LogMaskException("Unable to generate a safe object representation", exception);
        }
    }

    public static final class Builder {
        private final ObjectMapper objectMapper;

        private Builder(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        }

        public LogMasker build() {
            return new LogMasker(objectMapper);
        }
    }
}
