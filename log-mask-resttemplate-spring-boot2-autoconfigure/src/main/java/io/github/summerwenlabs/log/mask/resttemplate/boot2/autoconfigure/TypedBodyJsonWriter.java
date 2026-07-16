package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.lang.reflect.Type;
import java.util.IdentityHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.BoundedMaskResult;
import io.github.summerwenlabs.log.mask.LogMasker;
import io.github.summerwenlabs.log.mask.MaskStrategyRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;

/**
 * Produces governed JSON from a converter's typed Java body value.
 *
 * <p>Mapper selection mirrors the converter's media-type registrations. Each
 * mapper receives an isolated, cached {@link LogMasker}; the application mapper
 * itself is never mutated. Failures become a body processing state.
 *
 * @author SummerWen
 * @since 0.1
 */
final class TypedBodyJsonWriter {

    private final AbstractJackson2HttpMessageConverter converter;
    private final boolean governanceEnabled;
    private final int maxBodyBytes;
    private final MaskStrategyRegistry strategyRegistry;
    private final Map<ObjectMapper, LogMasker> maskers =
            new IdentityHashMap<ObjectMapper, LogMasker>();

    TypedBodyJsonWriter(
            AbstractJackson2HttpMessageConverter converter,
            boolean governanceEnabled,
            int maxBodyBytes,
            MaskStrategyRegistry strategyRegistry) {
        this.converter = converter;
        this.governanceEnabled = governanceEnabled;
        this.maxBodyBytes = maxBodyBytes;
        this.strategyRegistry = strategyRegistry;
    }

    ObservedBody write(
            Object value,
            Type declaredType,
            Class<?> mapperTargetType,
            MediaType mediaType) {
        try {
            LogMasker masker = maskerFor(selectObjectMapper(mapperTargetType, mediaType));
            BoundedMaskResult result = masker.mask(
                    value,
                    declaredType,
                    maxBodyBytes);
            return result.isLimitExceeded()
                    ? ObservedBody.limitExceeded()
                    : ObservedBody.success(result.getJson());
        } catch (RuntimeException ignored) {
            return ObservedBody.processingFailed();
        }
    }

    private ObjectMapper selectObjectMapper(
            Class<?> targetType,
            MediaType mediaType) {
        ObjectMapper defaultMapper = converter.getObjectMapper();
        if (mediaType == null) {
            return defaultMapper;
        }
        Map<MediaType, ObjectMapper> registrations =
                converter.getObjectMappersForType(targetType);
        for (Map.Entry<MediaType, ObjectMapper> registration : registrations.entrySet()) {
            if (registration.getKey().includes(mediaType)) {
                return registration.getValue();
            }
        }
        return defaultMapper;
    }

    private LogMasker maskerFor(ObjectMapper objectMapper) {
        synchronized (maskers) {
            LogMasker masker = maskers.get(objectMapper);
            if (masker == null) {
                masker = LogMasker.builder(objectMapper)
                        .strategyRegistry(strategyRegistry)
                        .governanceEnabled(governanceEnabled)
                        .build();
                maskers.put(objectMapper, masker);
            }
            return masker;
        }
    }
}
