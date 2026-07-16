package io.github.summerwenlabs.log.mask.http;

import java.io.IOException;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Writes compact, single-line schema version 1 HTTP exchange events.
 *
 * <p>Instances are thread-safe. The writer owns only JSON rendering and does
 * not choose a logging backend or log level. Timestamps are rendered in UTC
 * with millisecond precision.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class HttpExchangeEventWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private final JsonFactory jsonFactory = new JsonFactory();
    private final NameValueShape nameValueShape;
    private final boolean uriDetailsEnabled;

    /**
     * Create a writer using standard name/value arrays and URI details.
     */
    public HttpExchangeEventWriter() {
        this(NameValueShape.STANDARD, true);
    }

    /**
     * Create a writer with an explicit name/value shape and URI detail mode.
     * @param nameValueShape JSON representation for headers and query values
     * @param uriDetailsEnabled whether component fields accompany the full URI
     * @throws NullPointerException if {@code nameValueShape} is {@code null}
     */
    public HttpExchangeEventWriter(NameValueShape nameValueShape, boolean uriDetailsEnabled) {
        this.nameValueShape = Objects.requireNonNull(nameValueShape, "nameValueShape");
        this.uriDetailsEnabled = uriDetailsEnabled;
    }

    /**
     * Write one event as compact, single-line JSON.
     * @param event validated terminal exchange event
     * @return the complete JSON event
     * @throws NullPointerException if {@code event} is {@code null}
     * @throws HttpExchangeWriteException if JSON generation fails
     */
    public String write(HttpExchangeEvent event) {
        Objects.requireNonNull(event, "event");
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = jsonFactory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeStringField("event", HttpExchangeEvent.EVENT_NAME);
            generator.writeNumberField("schemaVersion", HttpExchangeEvent.SCHEMA_VERSION);
            generator.writeStringField(
                    "timestamp",
                    TIMESTAMP_FORMATTER.format(event.getTimestamp().truncatedTo(ChronoUnit.MILLIS)));
            generator.writeStringField("exchangeId", event.getExchangeId().toString());
            if (event.getTraceId() == null) {
                generator.writeNullField("traceId");
            } else {
                generator.writeStringField("traceId", event.getTraceId());
            }
            generator.writeNumberField("durationMs", event.getDurationMs());
            generator.writeBooleanField("governanceEnabled", event.isGovernanceEnabled());
            writeRequest(generator, event.getRequest());
            writeResponse(generator, event.getResponse());
            generator.writeEndObject();
        } catch (IOException exception) {
            throw new HttpExchangeWriteException("Unable to write the HTTP exchange event", exception);
        }
        return output.toString();
    }

    private void writeRequest(JsonGenerator generator, HttpExchangeRequest request) throws IOException {
        generator.writeObjectFieldStart("request");
        generator.writeStringField("method", request.getMethod());
        generator.writeStringField("uriState", request.getUriState().name());
        writeUri(generator, request.getUri());
        generator.writeStringField("headersState", request.getHeadersState().name());
        writeNameValueField(generator, "headers", request.getHeaders());
        generator.writeStringField("bodyState", request.getBodyState().name());
        writeJsonField(generator, "body", request.getBody());
        generator.writeEndObject();
    }

    private void writeResponse(JsonGenerator generator, HttpExchangeResponse response) throws IOException {
        if (response == null) {
            generator.writeNullField("response");
            return;
        }
        generator.writeObjectFieldStart("response");
        generator.writeNumberField("status", response.getStatus());
        generator.writeStringField("headersState", response.getHeadersState().name());
        writeNameValueField(generator, "headers", response.getHeaders());
        generator.writeStringField("bodyState", response.getBodyState().name());
        writeJsonField(generator, "body", response.getBody());
        generator.writeEndObject();
    }

    private void writeJsonField(JsonGenerator generator, String name, JsonValue value) throws IOException {
        generator.writeFieldName(name);
        value.writeTo(generator);
    }

    private void writeUri(JsonGenerator generator, HttpRequestUri uri) throws IOException {
        generator.writeObjectFieldStart("uri");
        generator.writeStringField("full", uri.getFull());
        if (uriDetailsEnabled) {
            writeNullableStringField(generator, "scheme", uri.getScheme());
            writeNullableStringField(generator, "host", uri.getHost());
            if (uri.getPort() < 0) {
                generator.writeNullField("port");
            } else {
                generator.writeNumberField("port", uri.getPort());
            }
            generator.writeStringField("path", uri.getPath());
            writeNameValueField(generator, "query", uri.getQuery());
        }
        generator.writeEndObject();
    }

    private void writeNullableStringField(JsonGenerator generator, String name, String value)
            throws IOException {
        if (value == null) {
            generator.writeNullField(name);
        } else {
            generator.writeStringField(name, value);
        }
    }

    private void writeNameValueField(
            JsonGenerator generator,
            String fieldName,
            NameValueCollection collection) throws IOException {
        if (collection == null) {
            generator.writeNullField(fieldName);
            return;
        }
        generator.writeFieldName(fieldName);
        if (nameValueShape == NameValueShape.STANDARD) {
            writeStandardNameValues(generator, collection);
        } else {
            writeCompactNameValues(generator, collection);
        }
    }

    private void writeStandardNameValues(
            JsonGenerator generator,
            NameValueCollection collection) throws IOException {
        generator.writeStartArray();
        for (NameValueEntry entry : collection.getEntries()) {
            generator.writeStartObject();
            generator.writeStringField("name", entry.getName());
            generator.writeArrayFieldStart("values");
            writeValues(generator, entry);
            generator.writeEndArray();
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeCompactNameValues(
            JsonGenerator generator,
            NameValueCollection collection) throws IOException {
        generator.writeStartObject();
        for (NameValueEntry entry : collection.getEntries()) {
            generator.writeArrayFieldStart(entry.getName());
            writeValues(generator, entry);
            generator.writeEndArray();
        }
        generator.writeEndObject();
    }

    private void writeValues(JsonGenerator generator, NameValueEntry entry) throws IOException {
        for (String value : entry.getValues()) {
            if (value == null) {
                generator.writeNull();
            } else {
                generator.writeString(value);
            }
        }
    }
}
