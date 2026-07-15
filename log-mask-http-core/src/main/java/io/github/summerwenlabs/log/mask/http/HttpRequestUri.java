package io.github.summerwenlabs.log.mask.http;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable log representation of an HTTP request URI.
 */
public final class HttpRequestUri {

    private final RegionState state;
    private final String full;
    private final String scheme;
    private final String host;
    private final int port;
    private final String path;
    private final NameValueCollection query;

    private HttpRequestUri(
            RegionState state,
            String full,
            String scheme,
            String host,
            int port,
            String path,
            NameValueCollection query) {
        this.state = state;
        this.full = full;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.path = path;
        this.query = query;
    }

    public static HttpRequestUri from(URI requestUri) {
        Objects.requireNonNull(requestUri, "requestUri");
        boolean processed = isProcessableHttpUri(requestUri);
        String scheme = normalize(requestUri.getScheme());
        String host = normalize(requestUri.getHost());
        int explicitPort = requestUri.getPort();
        int port = processed
                ? (explicitPort >= 0 ? explicitPort : defaultPort(scheme))
                : fallbackPort(requestUri, scheme);
        String path = path(requestUri);
        String rawQuery = requestUri.getRawQuery();
        String full = processed
                ? rebuildHttpUri(requestUri, scheme, host, path, rawQuery)
                : rebuildFallbackUri(requestUri, scheme, host, path, rawQuery);
        return new HttpRequestUri(
                processed ? RegionState.SUCCESS : RegionState.PROCESSING_FAILED,
                full,
                scheme,
                host,
                port,
                path,
                parseQuery(rawQuery));
    }

    private static String rebuildHttpUri(
            URI requestUri,
            String scheme,
            String host,
            String path,
            String rawQuery) {
        StringBuilder full = new StringBuilder();
        full.append(scheme).append("://").append(host);
        if (requestUri.getPort() >= 0) {
            full.append(':').append(explicitPortText(requestUri));
        }
        appendPathAndQuery(full, path, rawQuery);
        return full.toString();
    }

    private static String rebuildFallbackUri(
            URI requestUri,
            String scheme,
            String host,
            String path,
            String rawQuery) {
        StringBuilder full = new StringBuilder();
        if (scheme != null) {
            full.append(scheme).append(':');
        }
        String authority = requestUri.getRawAuthority();
        if (authority != null) {
            full.append("//");
            if (host == null) {
                full.append(withoutUserInfo(authority));
            } else {
                full.append(host);
                if (requestUri.getPort() >= 0) {
                    full.append(':').append(explicitPortText(requestUri));
                }
            }
        }
        appendPathAndQuery(full, path, rawQuery);
        return full.toString();
    }

    private static void appendPathAndQuery(
            StringBuilder full,
            String path,
            String rawQuery) {
        full.append(path);
        if (rawQuery != null) {
            full.append('?').append(rawQuery);
        }
    }

    public RegionState getState() {
        return state;
    }

    public String getFull() {
        return full;
    }

    public String getScheme() {
        return scheme;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public NameValueCollection getQuery() {
        return query;
    }

    private static int defaultPort(String scheme) {
        if ("http".equals(scheme)) {
            return 80;
        }
        if ("https".equals(scheme)) {
            return 443;
        }
        return -1;
    }

    private static int fallbackPort(URI requestUri, String scheme) {
        if (requestUri.getPort() >= 0) {
            return requestUri.getPort();
        }
        String authority = requestUri.getRawAuthority();
        if (authority == null) {
            return defaultPort(scheme);
        }
        String target = withoutUserInfo(authority);
        if (target.startsWith("[")) {
            int closingBracket = target.indexOf(']');
            if (closingBracket < 0 || closingBracket == target.length() - 1) {
                return closingBracket < 0 ? -1 : defaultPort(scheme);
            }
            if (target.charAt(closingBracket + 1) != ':') {
                return -1;
            }
            return parsePort(target.substring(closingBracket + 2));
        }

        int firstColon = target.indexOf(':');
        if (firstColon < 0) {
            return defaultPort(scheme);
        }
        if (firstColon != target.lastIndexOf(':')) {
            return -1;
        }
        return parsePort(target.substring(firstColon + 1));
    }

    private static int parsePort(String value) {
        if (value.isEmpty()) {
            return -1;
        }
        int port = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return -1;
            }
            int digit = character - '0';
            if (port > (Integer.MAX_VALUE - digit) / 10) {
                return -1;
            }
            port = port * 10 + digit;
        }
        return port;
    }

    private static String explicitPortText(URI requestUri) {
        String authority = requestUri.getRawAuthority();
        int separator = authority.lastIndexOf(':');
        return authority.substring(separator + 1);
    }

    private static boolean isProcessableHttpUri(URI requestUri) {
        String scheme = normalize(requestUri.getScheme());
        return !requestUri.isOpaque()
                && ("http".equals(scheme) || "https".equals(scheme))
                && requestUri.getHost() != null
                && !requestUri.getHost().isEmpty()
                && requestUri.getRawAuthority() != null
                && !withoutUserInfo(requestUri.getRawAuthority()).endsWith(":");
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String path(URI requestUri) {
        String path = requestUri.getRawPath();
        return path == null ? "" : path;
    }

    private static String withoutUserInfo(String authority) {
        int separator = authority.lastIndexOf('@');
        return separator < 0 ? authority : authority.substring(separator + 1);
    }

    private static NameValueCollection parseQuery(String rawQuery) {
        NameValueCollection.Builder query = NameValueCollection.builder();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query.build();
        }
        String[] elements = rawQuery.split("&", -1);
        for (String element : elements) {
            int separator = element.indexOf('=');
            if (separator < 0) {
                query.add(decodeQueryComponent(element), null);
            } else {
                query.add(
                        decodeQueryComponent(element.substring(0, separator)),
                        decodeQueryComponent(element.substring(separator + 1)));
            }
        }
        return query.build();
    }

    private static String decodeQueryComponent(String raw) {
        if (raw.indexOf('%') < 0) {
            return raw;
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
                    return raw;
                }
                int high = Character.digit(raw.charAt(index + 1), 16);
                int low = Character.digit(raw.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    return raw;
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
                return raw;
            }
        }
        return decoded.toString();
    }
}
