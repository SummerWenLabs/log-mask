package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.http.converter.ByteArrayHttpMessageConverter;

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
