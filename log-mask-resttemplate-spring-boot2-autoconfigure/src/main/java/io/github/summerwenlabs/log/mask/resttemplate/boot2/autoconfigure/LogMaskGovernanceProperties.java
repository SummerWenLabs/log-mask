package io.github.summerwenlabs.log.mask.resttemplate.boot2.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds explicit HTTP log data governance rules under
 * {@code log-mask.governance}.
 *
 * <p>No sensitive names or values are inferred. An empty rule list preserves
 * values as observed, and request and response header rules remain independent.
 *
 * @author SummerWen
 * @since 0.1
 */
@ConfigurationProperties(prefix = "log-mask.governance")
public class LogMaskGovernanceProperties {

    private boolean enabled = true;
    private Http http = new Http();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http == null ? new Http() : http;
    }

    /** Groups client-independent HTTP path, query, and header rules. */
    public static final class Http {

        private Path path = new Path();
        private Query query = new Query();
        private Headers headers = new Headers();

        public Path getPath() {
            return path;
        }

        public void setPath(Path path) {
            this.path = path == null ? new Path() : path;
        }

        public Query getQuery() {
            return query;
        }

        public void setQuery(Query query) {
            this.query = query == null ? new Query() : query;
        }

        public Headers getHeaders() {
            return headers;
        }

        public void setHeaders(Headers headers) {
            this.headers = headers == null ? new Headers() : headers;
        }
    }

    /** Holds ordered path template declarations. */
    public static final class Path {

        private List<PathRule> rules = new ArrayList<PathRule>();

        public List<PathRule> getRules() {
            return rules;
        }

        public void setRules(List<PathRule> rules) {
            this.rules = rules == null ? new ArrayList<PathRule>() : rules;
        }
    }

    /** Holds ordered query value declarations. */
    public static final class Query {

        private List<ValueRule> rules = new ArrayList<ValueRule>();

        public List<ValueRule> getRules() {
            return rules;
        }

        public void setRules(List<ValueRule> rules) {
            this.rules = rules == null ? new ArrayList<ValueRule>() : rules;
        }
    }

    /** Separates request and response header rule sets. */
    public static final class Headers {

        private Direction request = new Direction();
        private Direction response = new Direction();

        public Direction getRequest() {
            return request;
        }

        public void setRequest(Direction request) {
            this.request = request == null ? new Direction() : request;
        }

        public Direction getResponse() {
            return response;
        }

        public void setResponse(Direction response) {
            this.response = response == null ? new Direction() : response;
        }
    }

    /** Holds rules for one HTTP message direction. */
    public static final class Direction {

        private List<ValueRule> rules = new ArrayList<ValueRule>();

        public List<ValueRule> getRules() {
            return rules;
        }

        public void setRules(List<ValueRule> rules) {
            this.rules = rules == null ? new ArrayList<ValueRule>() : rules;
        }
    }

    /** Binds one path template, optional scope, and named variables. */
    public static final class PathRule {

        private String pattern;
        private String host;
        private String method;
        private List<PathVariable> variables = new ArrayList<PathVariable>();

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public List<PathVariable> getVariables() {
            return variables;
        }

        public void setVariables(List<PathVariable> variables) {
            this.variables = variables == null
                    ? new ArrayList<PathVariable>()
                    : variables;
        }
    }

    /** Binds one path variable to either a built-in type or custom code. */
    public static final class PathVariable {

        private String name;
        private String type;
        private String typeCode;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTypeCode() {
            return typeCode;
        }

        public void setTypeCode(String typeCode) {
            this.typeCode = typeCode;
        }
    }

    /** Binds one query or header name to a built-in type or custom code. */
    public static final class ValueRule {

        private String name;
        private String host;
        private String type;
        private String typeCode;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTypeCode() {
            return typeCode;
        }

        public void setTypeCode(String typeCode) {
            this.typeCode = typeCode;
        }
    }
}
