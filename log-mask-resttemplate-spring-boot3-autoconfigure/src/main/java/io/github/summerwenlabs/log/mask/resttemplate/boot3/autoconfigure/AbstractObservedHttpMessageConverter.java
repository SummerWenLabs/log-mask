/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot3.autoconfigure;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

/**
 * Decorates a concrete RestTemplate converter while preserving its behavior.
 *
 * <p>Capability checks and HTTP reads/writes are delegated unchanged. The same
 * Java value is observed only after successful conversion; a converter failure
 * is rethrown unchanged after best-effort request-only finalization.
 *
 * @author SummerWen
 * @since 0.1
 */
abstract class AbstractObservedHttpMessageConverter<T>
        implements HttpMessageConverter<T> {

    private final HttpMessageConverter<T> delegate;
    private final RestTemplateObservationRuntime runtime;

    AbstractObservedHttpMessageConverter(
            HttpMessageConverter<T> delegate,
            RestTemplateObservationRuntime runtime) {
        this.delegate = delegate;
        this.runtime = runtime;
    }

    @Override
    public boolean canRead(Class<?> type, MediaType mediaType) {
        return delegate.canRead(type, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> type, MediaType mediaType) {
        return delegate.canWrite(type, mediaType);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return delegate.getSupportedMediaTypes();
    }

    @Override
    public List<MediaType> getSupportedMediaTypes(Class<?> type) {
        return delegate.getSupportedMediaTypes(type);
    }

    @Override
    public T read(Class<? extends T> type, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        T value = delegate.read(type, inputMessage);
        if (runtime.isInfoEnabled() && runtime.isResponseBodyEnabled()) {
            runtime.recordResponseBody(inputMessage, toObservedBody(value));
        }
        return value;
    }

    @Override
    public void write(
            T value,
            MediaType contentType,
            HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        try {
            delegate.write(value, contentType, outputMessage);
        } catch (IOException | RuntimeException | Error failure) {
            runtime.requestConverterFailed(outputMessage);
            throw failure;
        }
        if (runtime.isInfoEnabled() && runtime.isRequestBodyEnabled()) {
            runtime.offerRequestBody(outputMessage, toObservedBody(value));
        }
    }

    abstract ObservedBody toObservedBody(T value);
}
