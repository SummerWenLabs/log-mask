/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.governance;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decodes strict UTF-8 path segments and encodes governed replacements.
 *
 * <p>Encoding emits only RFC 3986 unreserved characters directly and uses
 * upper-case percent triplets for every other UTF-8 byte.
 *
 * @author SummerWen
 * @since 0.1
 */
final class UriPathCodec {

    private UriPathCodec() {
    }

    static Decoded decode(String raw) {
        if (raw.indexOf('%') < 0) {
            return Decoded.success(raw);
        }
        StringBuilder decoded = new StringBuilder(raw.length());
        int index = 0;
        while (index < raw.length()) {
            if (raw.charAt(index) != '%') {
                decoded.append(raw.charAt(index));
                index++;
                continue;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (index < raw.length() && raw.charAt(index) == '%') {
                if (index + 2 >= raw.length()) {
                    return Decoded.failure();
                }
                int high = Character.digit(raw.charAt(index + 1), 16);
                int low = Character.digit(raw.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    return Decoded.failure();
                }
                bytes.write((high << 4) + low);
                index += 3;
            }
            try {
                decoded.append(StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toByteArray())));
            } catch (CharacterCodingException exception) {
                return Decoded.failure();
            }
        }
        return Decoded.success(decoded.toString());
    }

    static String encode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte current : bytes) {
            int unsigned = current & 0xff;
            if (isUnreserved(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0x0f, 16)));
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9')
                || value == '-'
                || value == '.'
                || value == '_'
                || value == '~';
    }

    static final class Decoded {
        private final String value;
        private final boolean successful;

        private Decoded(String value, boolean successful) {
            this.value = value;
            this.successful = successful;
        }

        static Decoded success(String value) {
            return new Decoded(value, true);
        }

        static Decoded failure() {
            return new Decoded(null, false);
        }

        String value() {
            return value;
        }

        boolean successful() {
            return successful;
        }
    }
}
