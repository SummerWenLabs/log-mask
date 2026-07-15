package io.github.summerwenlabs.log.mask.http;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * A validated, compact JSON value ready to embed in an event.
 */
public final class JsonValue {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final JsonValue EMPTY_STRING = new JsonValue("\"\"");
    private static final JsonValue NULL = new JsonValue("null");

    private final String json;

    private JsonValue(String json) {
        this.json = json;
    }

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

    public static JsonValue emptyString() {
        return EMPTY_STRING;
    }

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
