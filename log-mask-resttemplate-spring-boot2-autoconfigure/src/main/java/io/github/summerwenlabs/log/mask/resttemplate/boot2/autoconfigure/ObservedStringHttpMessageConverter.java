package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.http.converter.StringHttpMessageConverter;

final class ObservedStringHttpMessageConverter
        extends AbstractObservedHttpMessageConverter<String> {

    private final RestTemplateObservationRuntime runtime;

    ObservedStringHttpMessageConverter(
            StringHttpMessageConverter delegate,
            RestTemplateObservationRuntime runtime) {
        super(delegate, runtime);
        this.runtime = runtime;
    }

    @Override
    ObservedBody toObservedBody(String value) {
        return runtime.writeStringBody(value);
    }
}
