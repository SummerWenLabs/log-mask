/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.http.governance;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.summerwenlabs.log.mask.http.NameValueCollection;
import io.github.summerwenlabs.log.mask.http.RegionState;

/**
 * Holds an immutable log representation of an HTTP request URI.
 *
 * <p>HTTP schemes and hosts are normalized, effective ports are reported, user
 * information and fragments are omitted, and raw path encoding is preserved
 * unless a named variable is governed. The full URI always reflects the final
 * governed path and query.
 *
 * @author SummerWen
 * @since 0.1
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

    /**
     * Create an observed URI without explicit query rules.
     * @param requestUri source URI
     * @return the normalized log representation
     * @throws NullPointerException if {@code requestUri} is {@code null}
     */
    public static HttpRequestUri from(URI requestUri) {
        return from(requestUri, HttpQueryGovernance.none());
    }

    /**
     * Create an observed URI with explicit query governance.
     * @param requestUri source URI
     * @param queryGovernance compiled query rules
     * @return the normalized and governed log representation
     * @throws NullPointerException if an argument is {@code null}
     */
    public static HttpRequestUri from(
            URI requestUri,
            HttpQueryGovernance queryGovernance) {
        return from(
                requestUri,
                queryGovernance,
                path(Objects.requireNonNull(requestUri, "requestUri")),
                false);
    }

    static HttpRequestUri fromGovernedPath(
            URI requestUri,
            HttpQueryGovernance queryGovernance,
            String governedPath,
            boolean pathFallbackApplied) {
        return from(
                requestUri,
                queryGovernance,
                Objects.requireNonNull(governedPath, "governedPath"),
                pathFallbackApplied);
    }

    private static HttpRequestUri from(
            URI requestUri,
            HttpQueryGovernance queryGovernance,
            String governedPath,
            boolean pathFallbackApplied) {
        Objects.requireNonNull(requestUri, "requestUri");
        Objects.requireNonNull(queryGovernance, "queryGovernance");
        boolean processed = isProcessableHttpUri(requestUri);
        String scheme = normalize(requestUri.getScheme());
        String host = normalize(requestUri.getHost());
        int explicitPort = requestUri.getPort();
        int port = processed
                ? (explicitPort >= 0 ? explicitPort : defaultPort(scheme))
                : fallbackPort(requestUri, scheme);
        String path = governedPath;
        QueryResult query = governQuery(
                requestUri.getRawQuery(),
                host,
                queryGovernance);
        String full = processed
                ? rebuildHttpUri(requestUri, scheme, host, path, query.rawQuery)
                : rebuildFallbackUri(requestUri, scheme, host, path, query.rawQuery);
        return new HttpRequestUri(
                state(processed, query.fallbackApplied || pathFallbackApplied),
                full,
                scheme,
                host,
                port,
                path,
                query.values);
    }

    private static RegionState state(boolean processed, boolean fallbackApplied) {
        if (!processed) {
            return RegionState.PROCESSING_FAILED;
        }
        return fallbackApplied ? RegionState.FALLBACK_APPLIED : RegionState.SUCCESS;
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

    /**
     * Return the final URI execution state.
     * @return the URI state
     */
    public RegionState getState() {
        return state;
    }

    /**
     * Return the governed full URI without user information or fragment.
     * @return the full URI string
     */
    public String getFull() {
        return full;
    }

    /**
     * Return the normalized lower-case scheme.
     * @return scheme, or {@code null} when unavailable
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Return the normalized lower-case host.
     * @return host, or {@code null} when unavailable
     */
    public String getHost() {
        return host;
    }

    /**
     * Return the explicit or scheme-default effective port.
     * @return port, or {@code -1} when it cannot be determined
     */
    public int getPort() {
        return port;
    }

    /**
     * Return the governed raw path.
     * @return the path, possibly empty
     */
    public String getPath() {
        return path;
    }

    /**
     * Return the ordered governed query values.
     * @return query entries, empty when no query exists
     */
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

    private static QueryResult governQuery(
            String rawQuery,
            String host,
            HttpQueryGovernance governance) {
        NameValueCollection.Builder query = NameValueCollection.builder();
        if (rawQuery == null) {
            return new QueryResult(null, query.build(), false);
        }
        if (rawQuery.isEmpty()) {
            return new QueryResult("", query.build(), false);
        }
        List<String> rendered = new ArrayList<String>();
        boolean fallbackApplied = false;
        String[] elements = rawQuery.split("&", -1);
        for (String element : elements) {
            int separator = element.indexOf('=');
            boolean hasValue = separator >= 0;
            String rawName = hasValue ? element.substring(0, separator) : element;
            String rawValue = hasValue ? element.substring(separator + 1) : null;
            DecodedComponent name = decodeQueryComponent(rawName);
            DecodedComponent value = hasValue
                    ? decodeQueryComponent(rawValue)
                    : DecodedComponent.success(null);
            HttpQueryGovernance.QueryValue governed = name.decoded
                    ? governance.govern(
                            host,
                            name.value,
                            value.value,
                            value.decoded,
                            hasValue)
                    : HttpQueryGovernance.QueryValue.unchanged(value.value);
            if (governed.excluded) {
                continue;
            }
            query.add(name.value, governed.value);
            rendered.add(governed.governed
                    ? renderGovernedQueryElement(rawName, governed.value, hasValue)
                    : element);
            fallbackApplied |= governed.fallbackApplied;
        }
        String governedRawQuery = rendered.isEmpty() ? null : join(rendered);
        return new QueryResult(governedRawQuery, query.build(), fallbackApplied);
    }

    private static String renderGovernedQueryElement(
            String rawName,
            String value,
            boolean hasValue) {
        return hasValue ? rawName + '=' + encodeQueryValue(value) : rawName;
    }

    private static String join(List<String> elements) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < elements.size(); index++) {
            if (index > 0) {
                result.append('&');
            }
            result.append(elements.get(index));
        }
        return result.toString();
    }

    private static String encodeQueryValue(String value) {
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

    private static DecodedComponent decodeQueryComponent(String raw) {
        if (raw.indexOf('%') < 0) {
            return DecodedComponent.success(raw);
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
                    return DecodedComponent.failure(raw);
                }
                int high = Character.digit(raw.charAt(index + 1), 16);
                int low = Character.digit(raw.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    return DecodedComponent.failure(raw);
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
                return DecodedComponent.failure(raw);
            }
        }
        return DecodedComponent.success(decoded.toString());
    }

    private static final class QueryResult {
        private final String rawQuery;
        private final NameValueCollection values;
        private final boolean fallbackApplied;

        private QueryResult(
                String rawQuery,
                NameValueCollection values,
                boolean fallbackApplied) {
            this.rawQuery = rawQuery;
            this.values = values;
            this.fallbackApplied = fallbackApplied;
        }
    }

    private static final class DecodedComponent {
        private final String value;
        private final boolean decoded;

        private DecodedComponent(String value, boolean decoded) {
            this.value = value;
            this.decoded = decoded;
        }

        private static DecodedComponent success(String value) {
            return new DecodedComponent(value, true);
        }

        private static DecodedComponent failure(String rawValue) {
            return new DecodedComponent(rawValue, false);
        }
    }
}
