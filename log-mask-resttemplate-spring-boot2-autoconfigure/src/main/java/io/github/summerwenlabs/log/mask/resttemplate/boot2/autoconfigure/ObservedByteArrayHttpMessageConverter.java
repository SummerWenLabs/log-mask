package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.http.converter.ByteArrayHttpMessageConverter;

/**
 * Observes byte-array converter values as Base64 JSON strings.
 *
 * <p>The delegate retains complete ownership of media type support and HTTP
 * conversion; bytes are never guessed to be text at this typed boundary.
 *
 * @author SummerWen
 * @since 0.1
 */
final class ObservedByteArrayHttpMessageConverter
        extends AbstractObservedHttpMessageConverter<byte[]> {

    private final RestTemplateObservationRuntime runtime;

    ObservedByteArrayHttpMessageConverter(
            ByteArrayHttpMessageConverter delegate,
            RestTemplateObservationRuntime runtime) {
        super(delegate, runtime);
        this.runtime = runtime;
    }

    @Override
    ObservedBody toObservedBody(byte[] value) {
        return runtime.writeByteArrayBody(value);
    }
}
