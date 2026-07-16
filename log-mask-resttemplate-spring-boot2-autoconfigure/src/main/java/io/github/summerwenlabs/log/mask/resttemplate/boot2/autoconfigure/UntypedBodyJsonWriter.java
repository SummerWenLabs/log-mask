/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.BoundedMaskResult;
import io.github.summerwenlabs.log.mask.LogMasker;
import org.springframework.http.MediaType;

/**
 * Produces JSON log values for strings, bytes, and error-response wire bytes.
 *
 * <p>Strings remain JSON strings and byte arrays use Jackson Base64 encoding.
 * Wire bytes use the declared charset, defaulting to UTF-8; undecodable bytes
 * fall back to the lossless byte-array representation without field governance.
 *
 * @author SummerWen
 * @since 0.1
 */
final class UntypedBodyJsonWriter {

    private final LogMasker jsonWriter = LogMasker.builder(new ObjectMapper())
            .governanceEnabled(false)
            .build();
    private final int maxBodyBytes;

    UntypedBodyJsonWriter(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    ObservedBody writeString(String value) {
        return write(value, String.class);
    }

    ObservedBody writeBytes(byte[] value) {
        return write(value, byte[].class);
    }

    ObservedBody writeWire(byte[] value, MediaType contentType) {
        if (value == null || value.length == 0) {
            return ObservedBody.absent();
        }
        if (value.length > maxWireBytes(contentType)) {
            return ObservedBody.limitExceeded();
        }
        try {
            Charset charset = charset(contentType);
            String text = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
            return writeString(text);
        } catch (CharacterCodingException | RuntimeException ignored) {
            return writeBytes(value);
        }
    }

    int maxWireBytes(MediaType contentType) {
        try {
            Charset charset = charset(contentType);
            if (StandardCharsets.UTF_8.equals(charset)) {
                return maxBodyBytes;
            }
            float expansion = charset.newEncoder().maxBytesPerChar();
            // Keep custom Charset implementations from making capture unbounded.
            double boundedExpansion = Math.min(16.0d, Math.max(1.0d, expansion));
            long maximum = (long) Math.ceil((maxBodyBytes + 2.0d) * boundedExpansion) + 16L;
            return maximum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maximum;
        } catch (RuntimeException ignored) {
            return maxBodyBytes;
        }
    }

    private Charset charset(MediaType contentType) {
        Charset declared = contentType == null ? null : contentType.getCharset();
        return declared == null ? StandardCharsets.UTF_8 : declared;
    }

    private ObservedBody write(Object value, Class<?> declaredType) {
        if (value == null) {
            return ObservedBody.absent();
        }
        try {
            BoundedMaskResult result = jsonWriter.mask(
                    value,
                    declaredType,
                    maxBodyBytes);
            return result.isLimitExceeded()
                    ? ObservedBody.limitExceeded()
                    : ObservedBody.success(result.getJson());
        } catch (RuntimeException ignored) {
            return ObservedBody.processingFailed();
        }
    }
}
