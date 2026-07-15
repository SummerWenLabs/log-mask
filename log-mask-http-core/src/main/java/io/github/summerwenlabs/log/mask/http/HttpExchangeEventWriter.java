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
 * Thread-safe writer for compact schemaVersion 1 HTTP exchange events.
 */
public final class HttpExchangeEventWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private final JsonFactory jsonFactory = new JsonFactory();

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
        writeJsonField(generator, "uri", request.getUri());
        generator.writeStringField("headersState", request.getHeadersState().name());
        writeJsonField(generator, "headers", request.getHeaders());
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
        writeJsonField(generator, "headers", response.getHeaders());
        generator.writeStringField("bodyState", response.getBodyState().name());
        writeJsonField(generator, "body", response.getBody());
        generator.writeEndObject();
    }

    private void writeJsonField(JsonGenerator generator, String name, JsonValue value) throws IOException {
        generator.writeFieldName(name);
        value.writeTo(generator);
    }
}
