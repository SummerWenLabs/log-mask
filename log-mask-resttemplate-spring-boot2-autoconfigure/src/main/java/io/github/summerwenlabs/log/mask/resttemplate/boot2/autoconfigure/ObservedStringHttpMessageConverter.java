package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import org.springframework.http.converter.StringHttpMessageConverter;

/**
 * Observes string converter values as JSON strings without parsing contents.
 *
 * <p>The delegate retains complete ownership of media type support, charset,
 * and HTTP conversion. A string containing JSON remains a JSON string in logs.
 *
 * @author SummerWen
 * @since 0.1
 */
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
