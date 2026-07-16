package io.github.summerwenlabs.log.mask.http.exchange;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Holds one validated, compact JSON value ready to embed in an event.
 *
 * <p>The value is immutable and written as JSON rather than as an escaped JSON
 * string. Construction accepts exactly one complete JSON value.
 *
 * @author SummerWen
 * @since 0.1
 */
public final class JsonValue {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final JsonValue EMPTY_STRING = new JsonValue("\"\"");
    private static final JsonValue NULL = new JsonValue("null");

    private final String json;

    private JsonValue(String json) {
        this.json = json;
    }

    /**
     * Parse and compact exactly one JSON value.
     * @param json complete JSON value, including scalar values
     * @return an immutable embeddable value
     * @throws NullPointerException if {@code json} is {@code null}
     * @throws IllegalArgumentException if JSON is invalid, empty, or has
     * trailing values
     */
    public static JsonValue ofJson(String json) {
        Objects.requireNonNull(json, "json");
        StringWriter compact = new StringWriter();
        try (JsonParser parser = JSON_FACTORY.createParser(json);
                JsonGenerator generator = JSON_FACTORY.createGenerator(compact)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == null) {
                throw new IllegalArgumentException("json must contain one value");
            }
            generator.copyCurrentStructure(parser);
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("json must contain exactly one value");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("json must be a valid JSON value", exception);
        }
        return new JsonValue(compact.toString());
    }

    /**
     * Return the shared empty JSON string value.
     * @return JSON {@code ""}
     */
    public static JsonValue emptyString() {
        return EMPTY_STRING;
    }

    /**
     * Return the shared JSON null value.
     * @return JSON {@code null}
     */
    public static JsonValue nullValue() {
        return NULL;
    }

    void writeTo(JsonGenerator generator) throws IOException {
        generator.writeRawValue(json);
    }

    boolean isEmptyString() {
        return "\"\"".equals(json);
    }

}
