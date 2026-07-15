package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;

final class ObservedJacksonHttpMessageConverter
        implements GenericHttpMessageConverter<Object> {

    private final AbstractJackson2HttpMessageConverter delegate;
    private final RestTemplateObservationRuntime runtime;
    private final TypedBodyJsonWriter bodyWriter;

    ObservedJacksonHttpMessageConverter(
            AbstractJackson2HttpMessageConverter delegate,
            RestTemplateObservationRuntime runtime) {
        this.delegate = delegate;
        this.runtime = runtime;
        this.bodyWriter = new TypedBodyJsonWriter(
                delegate,
                runtime.isGovernanceEnabled(),
                runtime.maxBodyBytes(),
                runtime.strategyRegistry());
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return delegate.canRead(clazz, mediaType);
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return delegate.canRead(type, contextClass, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return delegate.canWrite(clazz, mediaType);
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return delegate.canWrite(type, clazz, mediaType);
    }

    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return delegate.getSupportedMediaTypes();
    }

    @Override
    public List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
        return delegate.getSupportedMediaTypes(clazz);
    }

    @Override
    public Object read(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        runtime.beginJacksonResponseRead();
        Object value = delegate.read(clazz, inputMessage);
        observeResponse(inputMessage, value, clazz, clazz);
        return value;
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        runtime.beginJacksonResponseRead();
        Object value = delegate.read(type, contextClass, inputMessage);
        Type declaredType = contextClass == null
                ? type
                : GenericTypeResolver.resolveType(type, contextClass);
        observeResponse(
                inputMessage,
                value,
                declaredType,
                rawClass(declaredType, value));
        return value;
    }

    @Override
    public void write(Object value, MediaType contentType, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        delegate.write(value, contentType, outputMessage);
        Type declaredType = value == null ? Object.class : value.getClass();
        observeRequest(
                outputMessage,
                value,
                declaredType,
                rawClass(declaredType, value));
    }

    @Override
    public void write(
            Object value,
            Type type,
            MediaType contentType,
            HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        delegate.write(value, type, contentType, outputMessage);
        Type declaredType = type == null
                ? (value == null ? Object.class : value.getClass())
                : type;
        Class<?> mapperTargetType = value == null
                ? rawClass(declaredType, null)
                : value.getClass();
        observeRequest(outputMessage, value, declaredType, mapperTargetType);
    }

    private void observeRequest(
            HttpOutputMessage outputMessage,
            Object value,
            Type declaredType,
            Class<?> mapperTargetType) {
        if (runtime.isInfoEnabled() && runtime.isRequestBodyEnabled()) {
            runtime.offerRequestBody(
                    outputMessage,
                    bodyWriter.write(
                            value,
                            declaredType,
                            mapperTargetType,
                            outputMessage.getHeaders().getContentType()));
        }
    }

    private void observeResponse(
            HttpInputMessage inputMessage,
            Object value,
            Type declaredType,
            Class<?> mapperTargetType) {
        if (runtime.isInfoEnabled() && runtime.isResponseBodyEnabled()) {
            runtime.recordResponseBody(
                    bodyWriter.write(
                            value,
                            declaredType,
                            mapperTargetType,
                            inputMessage.getHeaders().getContentType()));
        }
    }

    private Class<?> rawClass(Type declaredType, Object value) {
        if (declaredType != null) {
            return delegate.getObjectMapper()
                    .getTypeFactory()
                    .constructType(declaredType)
                    .getRawClass();
        }
        return value == null ? Object.class : value.getClass();
    }
}
