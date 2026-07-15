package io.github.summerwenlabs.log.mask;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;

final class BoundedJsonGenerator extends JsonGeneratorDelegate {

    private final BoundedUtf8OutputStream output;

    BoundedJsonGenerator(JsonGenerator delegate, BoundedUtf8OutputStream output) {
        super(delegate, false);
        this.output = output;
    }

    @Override
    public void writeStartArray() throws IOException {
        super.writeStartArray();
        verifyLimit();
    }

    @Override
    public void writeStartArray(int size) throws IOException {
        super.writeStartArray(size);
        verifyLimit();
    }

    @Override
    public void writeStartArray(Object value) throws IOException {
        super.writeStartArray(value);
        verifyLimit();
    }

    @Override
    public void writeStartArray(Object value, int size) throws IOException {
        super.writeStartArray(value, size);
        verifyLimit();
    }

    @Override
    public void writeEndArray() throws IOException {
        super.writeEndArray();
        verifyLimit();
    }

    @Override
    public void writeStartObject() throws IOException {
        super.writeStartObject();
        verifyLimit();
    }

    @Override
    public void writeStartObject(Object value) throws IOException {
        super.writeStartObject(value);
        verifyLimit();
    }

    @Override
    public void writeStartObject(Object value, int size) throws IOException {
        super.writeStartObject(value, size);
        verifyLimit();
    }

    @Override
    public void writeEndObject() throws IOException {
        super.writeEndObject();
        verifyLimit();
    }

    @Override
    public void writeFieldName(String name) throws IOException {
        super.writeFieldName(name);
        verifyLimit();
    }

    @Override
    public void writeFieldName(SerializableString name) throws IOException {
        super.writeFieldName(name);
        verifyLimit();
    }

    @Override
    public void writeFieldId(long id) throws IOException {
        super.writeFieldId(id);
        verifyLimit();
    }

    @Override
    public void writeArray(int[] values, int offset, int length) throws IOException {
        writeArrayElements(values, offset, length, index -> writeNumber(values[index]));
    }

    @Override
    public void writeArray(long[] values, int offset, int length) throws IOException {
        writeArrayElements(values, offset, length, index -> writeNumber(values[index]));
    }

    @Override
    public void writeArray(double[] values, int offset, int length) throws IOException {
        writeArrayElements(values, offset, length, index -> writeNumber(values[index]));
    }

    @Override
    public void writeArray(String[] values, int offset, int length) throws IOException {
        writeArrayElements(values, offset, length, index -> writeString(values[index]));
    }

    @Override
    public void writeString(String value) throws IOException {
        super.writeString(value);
        verifyLimit();
    }

    @Override
    public void writeString(Reader reader, int length) throws IOException {
        super.writeString(reader, length);
        verifyLimit();
    }

    @Override
    public void writeString(char[] values, int offset, int length) throws IOException {
        super.writeString(values, offset, length);
        verifyLimit();
    }

    @Override
    public void writeString(SerializableString value) throws IOException {
        super.writeString(value);
        verifyLimit();
    }

    @Override
    public void writeRawUTF8String(byte[] values, int offset, int length) throws IOException {
        super.writeRawUTF8String(values, offset, length);
        verifyLimit();
    }

    @Override
    public void writeUTF8String(byte[] values, int offset, int length) throws IOException {
        super.writeUTF8String(values, offset, length);
        verifyLimit();
    }

    @Override
    public void writeRaw(String value) throws IOException {
        super.writeRaw(value);
        verifyLimit();
    }

    @Override
    public void writeRaw(String value, int offset, int length) throws IOException {
        super.writeRaw(value, offset, length);
        verifyLimit();
    }

    @Override
    public void writeRaw(SerializableString value) throws IOException {
        super.writeRaw(value);
        verifyLimit();
    }

    @Override
    public void writeRaw(char[] values, int offset, int length) throws IOException {
        super.writeRaw(values, offset, length);
        verifyLimit();
    }

    @Override
    public void writeRaw(char value) throws IOException {
        super.writeRaw(value);
        verifyLimit();
    }

    @Override
    public void writeRawValue(String value) throws IOException {
        super.writeRawValue(value);
        verifyLimit();
    }

    @Override
    public void writeRawValue(String value, int offset, int length) throws IOException {
        super.writeRawValue(value, offset, length);
        verifyLimit();
    }

    @Override
    public void writeRawValue(char[] values, int offset, int length) throws IOException {
        super.writeRawValue(values, offset, length);
        verifyLimit();
    }

    @Override
    public void writeBinary(
            Base64Variant variant,
            byte[] values,
            int offset,
            int length) throws IOException {
        super.writeBinary(variant, values, offset, length);
        verifyLimit();
    }

    @Override
    public int writeBinary(Base64Variant variant, InputStream input, int length)
            throws IOException {
        int bytesRead = super.writeBinary(variant, input, length);
        verifyLimit();
        return bytesRead;
    }

    @Override
    public void writeNumber(short value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(int value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(long value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(BigInteger value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(double value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(float value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(BigDecimal value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(String value) throws IOException {
        super.writeNumber(value);
        verifyLimit();
    }

    @Override
    public void writeNumber(char[] values, int offset, int length) throws IOException {
        super.writeNumber(values, offset, length);
        verifyLimit();
    }

    @Override
    public void writeBoolean(boolean value) throws IOException {
        super.writeBoolean(value);
        verifyLimit();
    }

    @Override
    public void writeNull() throws IOException {
        super.writeNull();
        verifyLimit();
    }

    @Override
    public void writeOmittedField(String fieldName) throws IOException {
        super.writeOmittedField(fieldName);
        verifyLimit();
    }

    @Override
    public void writeObjectId(Object id) throws IOException {
        super.writeObjectId(id);
        verifyLimit();
    }

    @Override
    public void writeObjectRef(Object id) throws IOException {
        super.writeObjectRef(id);
        verifyLimit();
    }

    @Override
    public void writeTypeId(Object id) throws IOException {
        super.writeTypeId(id);
        verifyLimit();
    }

    @Override
    public void writeEmbeddedObject(Object value) throws IOException {
        super.writeEmbeddedObject(value);
        verifyLimit();
    }

    @Override
    public void writePOJO(Object value) throws IOException {
        super.writePOJO(value);
        verifyLimit();
    }

    @Override
    public void writeObject(Object value) throws IOException {
        super.writeObject(value);
        verifyLimit();
    }

    @Override
    public void writeTree(TreeNode tree) throws IOException {
        super.writeTree(tree);
        verifyLimit();
    }

    @Override
    public void copyCurrentEvent(JsonParser parser) throws IOException {
        super.copyCurrentEvent(parser);
        verifyLimit();
    }

    @Override
    public void copyCurrentStructure(JsonParser parser) throws IOException {
        super.copyCurrentStructure(parser);
        verifyLimit();
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        verifyLimit();
    }

    private void verifyLimit() throws IOException {
        output.ensureWithinLimit(delegate.getOutputBuffered());
    }

    private void requireArray(Object values) {
        if (values == null) {
            throw new IllegalArgumentException("null array");
        }
    }

    private void writeArrayElements(
            Object values,
            int offset,
            int length,
            ArrayElementWriter elementWriter) throws IOException {
        requireArray(values);
        _verifyOffsets(java.lang.reflect.Array.getLength(values), offset, length);
        writeStartArray(values, length);
        int end = offset + length;
        for (int index = offset; index < end; index++) {
            elementWriter.write(index);
        }
        writeEndArray();
    }

    @FunctionalInterface
    private interface ArrayElementWriter {
        void write(int index) throws IOException;
    }
}
