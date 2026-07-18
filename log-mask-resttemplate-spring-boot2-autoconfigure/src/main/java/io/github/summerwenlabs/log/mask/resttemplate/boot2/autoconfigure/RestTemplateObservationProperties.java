/* SPDX-License-Identifier: Apache-2.0 */

package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.summerwenlabs.log.mask.http.exchange.NameValueShape;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Binds RestTemplate observation settings under
 * {@code log-mask.logging.rest-template}.
 *
 * <p>Disabling the adapter produces a complete observation bypass. Bean names
 * are explicit opt-ins; request and response region switches are independent.
 *
 * @author SummerWen
 * @since 0.1
 */
@ConfigurationProperties(prefix = "log-mask.logging.rest-template")
public class RestTemplateObservationProperties {

    private boolean enabled = true;
    private List<String> observedBeanNames = new ArrayList<String>();
    private Uri uri = new Uri();
    private NameValueShape nameValueShape = NameValueShape.STANDARD;
    private DataSize maxBodySize = DataSize.ofKilobytes(64);
    private Region request = new Region();
    private Region response = new Region();
    private TraceId traceId = new TraceId();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getObservedBeanNames() {
        return observedBeanNames;
    }

    /**
     * Replace the explicitly observed bean names.
     *
     * <p>A {@code null} value clears the selection. Non-null lists are copied.
     * @param observedBeanNames bean names to select, or {@code null} for none
     */
    public void setObservedBeanNames(List<String> observedBeanNames) {
        this.observedBeanNames = observedBeanNames == null
                ? new ArrayList<String>()
                : new ArrayList<String>(observedBeanNames);
    }

    public Uri getUri() {
        return uri;
    }

    /**
     * Replace the URI detail settings.
     *
     * <p>A {@code null} value restores the default settings.
     * @param uri settings to use, or {@code null} to restore defaults
     */
    public void setUri(Uri uri) {
        this.uri = uri == null ? new Uri() : uri;
    }

    public NameValueShape getNameValueShape() {
        return nameValueShape;
    }

    public void setNameValueShape(NameValueShape nameValueShape) {
        this.nameValueShape = nameValueShape;
    }

    public DataSize getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(DataSize maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public Region getRequest() {
        return request;
    }

    /**
     * Replace the request-region settings.
     *
     * <p>A {@code null} value restores the default settings.
     * @param request settings to use, or {@code null} to restore defaults
     */
    public void setRequest(Region request) {
        this.request = request == null ? new Region() : request;
    }

    public Region getResponse() {
        return response;
    }

    /**
     * Replace the response-region settings.
     *
     * <p>A {@code null} value restores the default settings.
     * @param response settings to use, or {@code null} to restore defaults
     */
    public void setResponse(Region response) {
        this.response = response == null ? new Region() : response;
    }

    public TraceId getTraceId() {
        return traceId;
    }

    /**
     * Replace the trace-ID lookup settings.
     *
     * <p>A {@code null} value restores the default settings.
     * @param traceId settings to use, or {@code null} to restore defaults
     */
    public void setTraceId(TraceId traceId) {
        this.traceId = traceId == null ? new TraceId() : traceId;
    }

    /** Configures headers and body output for one HTTP message direction. */
    public static final class Region {

        private boolean headersEnabled = true;
        private boolean bodyEnabled = true;

        public boolean isHeadersEnabled() {
            return headersEnabled;
        }

        public void setHeadersEnabled(boolean headersEnabled) {
            this.headersEnabled = headersEnabled;
        }

        public boolean isBodyEnabled() {
            return bodyEnabled;
        }

        public void setBodyEnabled(boolean bodyEnabled) {
            this.bodyEnabled = bodyEnabled;
        }
    }

    /** Configures whether the full URI is accompanied by component details. */
    public static final class Uri {

        private boolean detailsEnabled = true;

        public boolean isDetailsEnabled() {
            return detailsEnabled;
        }

        public void setDetailsEnabled(boolean detailsEnabled) {
            this.detailsEnabled = detailsEnabled;
        }
    }

    /** Configures ordered, read-only lookup of host-provided MDC trace IDs. */
    public static final class TraceId {

        private boolean enabled = true;
        private List<String> mdcKeys = new ArrayList<String>(
                Arrays.asList("traceId", "trace_id"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getMdcKeys() {
            return mdcKeys;
        }

        /**
         * Replace the ordered MDC lookup keys.
         *
         * <p>A {@code null} value clears the keys. Non-null lists are copied.
         * @param mdcKeys lookup keys, or {@code null} to configure none
         */
        public void setMdcKeys(List<String> mdcKeys) {
            this.mdcKeys = mdcKeys == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(mdcKeys);
        }
    }
}
