# Log Mask RestTemplate Starter for Spring Boot 2

**English** · [简体中文](README.zh-CN.md)

> Add consistent call logs to selected `RestTemplate` beans and mask sensitive data in those logs.

## Why use it

1. `RestTemplate` does not provide ready-to-use request, response, and duration
   logs for production troubleshooting.
2. When each application writes these logs itself, formats differ and masking
   logic has to be built repeatedly.
3. Removing logs to pass a security review leaves too little information for
   later troubleshooting.

## Module overview

This starter supports Java 8, Spring Boot 2.6.15, and Spring Boot 2.7.18. Add
`@ObservedRestTemplate` to a `RestTemplate` bean, and its HTTP calls can be
written as JSON through the `log.mask.http` logger.

Masking changes only log content. It does not modify real requests, responses,
application objects, MDC, RestTemplate settings, or business exceptions.

Log Mask processes only data covered by an annotation or configuration rule.
Path segments, query parameters, headers, and body fields without a matching
rule are logged unchanged, so configure every value that must not appear in
clear text.

## Features

- Select clients with `@ObservedRestTemplate`.
- When observation is enabled and `log.mask.http` accepts `INFO`, write at most
  one JSON log entry for each completed HTTP call, with request and response in
  the same entry.
- Mask path segments, query parameters, request headers, and response headers.
- Process request and response bodies with `@Mask` and `@LogExclude`.
- Turn request headers, request bodies, response headers, and response bodies
  on or off separately.
- Read a trace ID from MDC without modifying MDC.

## Quick start

### Maven

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 1. Select a RestTemplate

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Bean
@ObservedRestTemplate
public RestTemplate partnerRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

### 2. Mark body fields

```java
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;

public final class PartnerRequest {
    private final String phone;
    private final String accessToken;

    public PartnerRequest(String phone, String accessToken) {
        this.phone = phone;
        this.accessToken = accessToken;
    }

    @Mask(type = MaskType.PHONE)
    public String getPhone() {
        return phone;
    }

    @LogExclude
    public String getAccessToken() {
        return accessToken;
    }
}
```

### 3. Enable call logging

```yaml
logging:
  level:
    log.mask.http: INFO
```

Actual request body:

```json
{"phone":"13800138000","accessToken":"actual-token"}
```

`request.body` in the log:

```json
{"phone":"138****8000"}
```

The real request still contains the original phone number and access token.
Only the log content changes.

## Select RestTemplate beans

### When the application has no RestTemplate

When the context has no `RestTemplate` bean, the starter creates
`logMaskRestTemplate` with the application's `RestTemplateBuilder`. Log Mask
does not configure its connection pool, timeouts, TLS, proxy, or retries.
Production applications should normally declare and configure their own
`RestTemplate`.

### When the application already has RestTemplate beans

Existing beans can be selected in three ways. The options can be combined, and
the same instance is not logged more than once.

1. Add the annotation to the bean:

```java
@Bean
@ObservedRestTemplate
public RestTemplate partnerRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

2. If the annotation cannot be added, configure the bean name in YAML:

```yaml
log-mask:
  logging:
    rest-template:
      observed-bean-names:
        - partnerRestTemplate
```

3. For code-based selection, declare a `RestTemplateObservationConfigurer`
   bean:

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot2.RestTemplateObservationConfigurer;
import org.springframework.context.annotation.Bean;

@Bean
public RestTemplateObservationConfigurer partnerSelection() {
    return (beanName, restTemplate) ->
            "partnerRestTemplate".equals(beanName);
}
```

A configured name must belong to the current `ApplicationContext`, and the bean
must be a `RestTemplate`. A missing name or a bean of another type stops
application startup.

## Log output

With Spring Boot's default logging configuration, call logs are written to the
console. They use the `log.mask.http` logger, and each message is complete JSON.
When `INFO` is not enabled for this logger, call content is neither collected
nor written.

To write these logs to a separate JSON Lines file, give the logger its own
appender and set `additivity` to `false`. Log Mask does not create appenders or
change the root logger.

Logback nodes for `logback-spring.xml`:

```xml
<appender name="LOG_MASK_HTTP" class="ch.qos.logback.core.FileAppender">
  <file>logs/log-mask-http.jsonl</file>
  <append>true</append>
  <encoder>
    <pattern>%msg%n</pattern>
    <charset>UTF-8</charset>
  </encoder>
</appender>

<logger name="log.mask.http" level="INFO" additivity="false">
  <appender-ref ref="LOG_MASK_HTTP" />
</logger>
```

Log4j2 nodes for `log4j2-spring.xml`:

```xml
<File name="LogMaskHttp" fileName="logs/log-mask-http.jsonl">
  <PatternLayout pattern="%m%n" charset="UTF-8" />
</File>

<Logger name="log.mask.http" level="info" additivity="false">
  <AppenderRef ref="LogMaskHttp" />
</Logger>
```

## Mask path segments

Configure the path template and each variable that needs masking:

```yaml
log-mask:
  governance:
    http:
      path:
        rules:
          - pattern: /customers/{id}
            variables:
              - name: id
                type: REDACT
```

Actual request:

```text
https://api.example.com/customers/42
```

URI in the log:

```json
{"request":{"uri":{"full":"https://api.example.com/customers/%3Credacted%3E","path":"/customers/%3Credacted%3E"}}}
```

`<redacted>` is URI-encoded as `%3Credacted%3E`. The real request still goes to
`/customers/42`. A rule can also be limited by `host` and `method`. Path
variables do not support `EXCLUDE`.

## Mask query parameters

Configure rules by query parameter name:

```yaml
log-mask:
  governance:
    http:
      query:
        rules:
          - name: token
            type: REDACT
```

Actual request:

```text
https://api.example.com/search?token=actual&visible=ok
```

URI in the log:

```json
{"request":{"uri":{"full":"https://api.example.com/search?token=%3Credacted%3E&visible=ok","query":[{"name":"token","values":["<redacted>"]},{"name":"visible","values":["ok"]}]}}}
```

The unconfigured `visible` parameter remains unchanged, and the real request
still sends `token=actual`. Query rules can be limited by `host`. Use `EXCLUDE`
to remove the whole parameter from the log.

## Mask request headers

Use `headers.request.rules` for request headers:

```yaml
log-mask:
  governance:
    http:
      headers:
        request:
          rules:
            - name: Authorization
              type: REDACT
```

Actual request header:

```text
Authorization: Bearer request-secret
```

`request.headers` in the log:

```json
[{"name":"authorization","values":["<redacted>"]}]
```

Header names are matched case-insensitively and appear in lowercase in the log.
The real request header is unchanged.

## Mask response headers

Use the separate `headers.response.rules` for response headers:

```yaml
log-mask:
  governance:
    http:
      headers:
        response:
          rules:
            - name: X-Response-Token
              type: REDACT
```

Actual response header:

```text
X-Response-Token: response-secret
```

`response.headers` in the log:

```json
[{"name":"x-response-token","values":["<redacted>"]}]
```

Request and response headers use separate rules. Query and header rules support
`REDACT`, `EXCLUDE`, `PHONE`, `EMAIL`, `ID_CARD`, `BANK_CARD`, and `FULL`.

## Mask request bodies

Add `@Mask` to a field or getter on the request object:

```java
@Mask(type = MaskType.PHONE)
public String getPhone() {
    return phone;
}
```

Actual body sent:

```json
{"phone":"13800138000"}
```

`request.body` in the log:

```json
{"phone":"138****8000"}
```

Use `@LogExclude` to remove a field completely from the log. The sent body and
the source request object are unchanged.

## Mask response bodies

Use the same field annotation on the response object:

```java
@Mask(type = MaskType.PHONE)
public String getPhone() {
    return phone;
}
```

Actual response and application object:

```json
{"phone":"13900139000"}
```

`response.body` in the log:

```json
{"phone":"139****9000"}
```

The response object returned to application code keeps the original value. For
field-level body masking, use a typed Java request or response object. JSON held
in a `String` is not processed by field annotations.

## Disable features

These switches have different effects:

| What to disable | Configuration | Effect |
| --- | --- | --- |
| All RestTemplate call logs | `log-mask.logging.rest-template.enabled=false` | Do not observe, collect, or write call logs |
| Log output temporarily | `logging.level.log.mask.http=OFF` | Do not collect or write call content |
| All masking and exclusion rules | `log-mask.governance.enabled=false` | Keep writing call logs, but record path, query, header, and body values unchanged |
| Request headers | `log-mask.logging.rest-template.request.headers-enabled=false` | Set `request.headers` to `null`; record other content normally |
| Request bodies | `log-mask.logging.rest-template.request.body-enabled=false` | Set `request.body` to an empty string; record other content normally |
| Response headers | `log-mask.logging.rest-template.response.headers-enabled=false` | Set `response.headers` to `null`; record other content normally |
| Response bodies | `log-mask.logging.rest-template.response.body-enabled=false` | Set `response.body` to an empty string; record other content normally |

For example, disable only response bodies:

```yaml
log-mask:
  logging:
    rest-template:
      response:
        body-enabled: false
```

`governance.enabled=false` does not turn logging off. It allows sensitive values
to enter logs unchanged, so use it only when raw content is intentional.

## Configuration

| Property | Default | Effect |
| --- | --- | --- |
| `log-mask.governance.enabled` | `true` | Apply all masking and exclusion rules |
| `log-mask.governance.http.path.rules` | `[]` | Path masking rules |
| `log-mask.governance.http.query.rules` | `[]` | Query parameter masking rules |
| `log-mask.governance.http.headers.request.rules` | `[]` | Request header masking rules |
| `log-mask.governance.http.headers.response.rules` | `[]` | Response header masking rules |
| `log-mask.logging.rest-template.enabled` | `true` | Master switch for RestTemplate call logs |
| `log-mask.logging.rest-template.observed-bean-names` | `[]` | Select RestTemplate beans by name in the current context |
| `log-mask.logging.rest-template.uri.details-enabled` | `true` | Add scheme, host, port, path, and query fields; the full URI remains when disabled |
| `log-mask.logging.rest-template.name-value-shape` | `STANDARD` | Use entry arrays for query and headers; `COMPACT` uses an object from names to value arrays |
| `log-mask.logging.rest-template.max-body-size` | `64KB` | Size limit for each request and response body in logs, from 1 to 2,147,483,647 bytes; an oversized body becomes an empty string while other content is still logged |
| `log-mask.logging.rest-template.request.headers-enabled` | `true` | Include request headers |
| `log-mask.logging.rest-template.request.body-enabled` | `true` | Include request bodies |
| `log-mask.logging.rest-template.response.headers-enabled` | `true` | Include response headers |
| `log-mask.logging.rest-template.response.body-enabled` | `true` | Include response bodies |
| `log-mask.logging.rest-template.trace-id.enabled` | `true` | Read a trace ID from MDC |
| `log-mask.logging.rest-template.trace-id.mdc-keys` | `[traceId, trace_id]` | Read keys in order and use the first non-empty value |

`STANDARD` output:

```json
[{"name":"token","values":["<redacted>"]}]
```

`COMPACT` output:

```json
{"token":["<redacted>"]}
```

An HTTP rule can use `type-code` to reference a `MaskTypeDefinition` bean from
the application. See [Custom strategies](../log-mask-core/README.md#custom-strategies)
in the Core guide.

## Related documentation

- [Core guide](../log-mask-core/README.md)
- [Runnable samples](../log-mask-samples/README.md)
- [Security policy](../SECURITY.md)
