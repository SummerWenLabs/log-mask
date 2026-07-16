package io.github.summerwenlabs.log.mask.http.governance;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;

import io.github.summerwenlabs.log.mask.http.NameValueEntry;
import io.github.summerwenlabs.log.mask.http.RegionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HttpRequestUriTest {

    @Test
    void rebuildsAnHttpUriFromNormalizedSafeComponentsWithoutChangingTheRequest() {
        URI requestUri = URI.create(
                "HTTPS://alice:secret@API.Example.COM:8443/a%2Fb?tag=java&tag=spring#profile");

        HttpRequestUri result = HttpRequestUri.from(requestUri);

        assertEquals(RegionState.SUCCESS, result.getState());
        assertEquals(
                "https://api.example.com:8443/a%2Fb?tag=java&tag=spring",
                result.getFull());
        assertEquals("https", result.getScheme());
        assertEquals("api.example.com", result.getHost());
        assertEquals(8443, result.getPort());
        assertEquals("/a%2Fb", result.getPath());
        assertEquals("tag", result.getQuery().getEntries().get(0).getName());
        assertEquals(
                Arrays.asList("java", "spring"),
                result.getQuery().getEntries().get(0).getValues());
        assertEquals(
                "HTTPS://alice:secret@API.Example.COM:8443/a%2Fb?tag=java&tag=spring#profile",
                requestUri.toString());
    }

    @Test
    void decodesEachQueryComponentStrictlyWithoutApplyingFormEncodingRules() {
        HttpRequestUri result = HttpRequestUri.from(URI.create(
                "https://example.com/search?flag&flag=&term=a+b&%74erm=second"
                        + "&space=a%20b&plus=a%2Bb&bad=%FF&ok=%E4%B8%AD"));

        assertEquals(Arrays.asList(null, ""), values(result, "flag"));
        assertEquals(Arrays.asList("a+b", "second"), values(result, "term"));
        assertEquals(Collections.singletonList("a b"), values(result, "space"));
        assertEquals(Collections.singletonList("a+b"), values(result, "plus"));
        assertEquals(Collections.singletonList("%FF"), values(result, "bad"));
        assertEquals(Collections.singletonList("\u4E2D"), values(result, "ok"));
        assertEquals(
                "https://example.com/search?flag&flag=&term=a+b&%74erm=second"
                        + "&space=a%20b&plus=a%2Bb&bad=%FF&ok=%E4%B8%AD",
                result.getFull());
    }

    @Test
    void keepsPortAndPathSpellingSeparateFromEffectiveConnectionDetails() {
        HttpRequestUri noPath = HttpRequestUri.from(URI.create("http://Example.COM"));
        HttpRequestUri rootPath = HttpRequestUri.from(URI.create("https://Example.COM/"));
        HttpRequestUri explicitPort =
                HttpRequestUri.from(URI.create("http://Example.COM:00080/items"));

        assertEquals("http://example.com", noPath.getFull());
        assertEquals(80, noPath.getPort());
        assertEquals("", noPath.getPath());
        assertEquals("https://example.com/", rootPath.getFull());
        assertEquals(443, rootPath.getPort());
        assertEquals("/", rootPath.getPath());
        assertEquals("http://example.com:00080/items", explicitPort.getFull());
        assertEquals(80, explicitPort.getPort());
    }

    @Test
    void fallsBackToSafeStructuredComponentsWhenOverallUriProcessingFails() {
        HttpRequestUri invalidAuthority = HttpRequestUri.from(URI.create(
                "http://alice:secret@foo_bar/path?good=ok#fragment"));
        HttpRequestUri opaque = HttpRequestUri.from(URI.create(
                "mailto:User@Example.COM?subject=secret#fragment"));

        assertEquals(RegionState.PROCESSING_FAILED, invalidAuthority.getState());
        assertEquals("http://foo_bar/path?good=ok", invalidAuthority.getFull());
        assertFalse(invalidAuthority.getFull().contains("alice"));
        assertFalse(invalidAuthority.getFull().contains("secret"));
        assertFalse(invalidAuthority.getFull().contains("fragment"));
        assertEquals(RegionState.PROCESSING_FAILED, opaque.getState());
        assertEquals("mailto:", opaque.getFull());
        assertFalse(opaque.getFull().contains("User@"));
        assertFalse(opaque.getFull().contains("subject"));
        assertFalse(opaque.getFull().contains("fragment"));
    }

    @Test
    void reportsARecoverableFallbackPortWithoutInventingAnInvalidOne() {
        HttpRequestUri registryAuthority = HttpRequestUri.from(URI.create(
                "http://alice:secret@FOO_BAR:0081/path"));
        HttpRequestUri invalidPort = HttpRequestUri.from(URI.create(
                "http://example.com:abc/path"));
        HttpRequestUri nonAsciiPort = HttpRequestUri.from(URI.create(
                "http://foo_bar:\uFF11\uFF12/path"));

        assertEquals(RegionState.PROCESSING_FAILED, registryAuthority.getState());
        assertEquals("http://FOO_BAR:0081/path", registryAuthority.getFull());
        assertEquals(81, registryAuthority.getPort());
        assertEquals(RegionState.PROCESSING_FAILED, invalidPort.getState());
        assertEquals(-1, invalidPort.getPort());
        assertEquals(RegionState.PROCESSING_FAILED, nonAsciiPort.getState());
        assertEquals(-1, nonAsciiPort.getPort());
    }

    private static java.util.List<String> values(HttpRequestUri uri, String name) {
        for (NameValueEntry entry : uri.getQuery().getEntries()) {
            if (name.equals(entry.getName())) {
                return entry.getValues();
            }
        }
        throw new AssertionError("Missing query entry: " + name);
    }
}
