# Log Mask RestTemplate Starter for Spring Boot 2

**English** | [简体中文](README.zh-CN.md)

`log-mask-resttemplate-spring-boot2-starter` adds request and response call logs to selected `RestTemplate` beans in Spring Boot 2 applications. It masks or excludes log content according to configuration.

It supports Java 8 and Spring Boot 2.6.15 and 2.7.18. For Spring Boot 3, use the [Spring Boot 3 Starter guide](../log-mask-resttemplate-spring-boot3-starter/README.md).

The log structure, content switches, and masking rules are shared by both Spring Boot generations. See the [RestTemplate common guide](../log-mask-resttemplate/README.md).

## Features

- Observe only RestTemplate instances selected by annotation, Bean name, or code.
- Write at most one complete JSON log entry for each HTTP call, with request and response in the same entry.
- Record request, response, duration, and Trace ID facts.
- Mask Path, Query, Request Header, and Response Header values.
- Use `@Mask` and `@LogExclude` for Request Body and Response Body fields.
- Enable or disable Request Header, Request Body, Response Header, and Response Body logging separately.
- Limit the logged size of each request and response Body.
- Change only log content; do not modify real requests, responses, application objects, MDC, or business exceptions.

## Quick start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The Starter includes Core and its matching autoconfigure module transitively. No additional Log Mask dependencies are normally required.

### 2. Define request and response fields

```java
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;

public final class User {

    public String username;

    @Mask(type = MaskType.FULL)
    public String customerCode;

    @LogExclude
    public String accessToken;

    public User() {
    }

    public User(String username, String customerCode, String accessToken) {
        this.username = username;
        this.customerCode = customerCode;
        this.accessToken = accessToken;
    }
}
```

### 3. Select a RestTemplate

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

`@ObservedRestTemplate` affects only this Bean. Other RestTemplate instances in the application are not observed automatically.

### 4. Enable call logs

```yaml
logging:
  level:
    log.mask.http: INFO
```

### 5. Make a request

Inject the `partnerRestTemplate` Bean into an application component. The following example assumes that `http://127.0.0.1:8080/user` returns a `User` with the same shape:

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public final class UserClient {

    private final RestTemplate restTemplate;

    public UserClient(
            @Qualifier("partnerRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<User> createUser() {
        User user = new User(
                "example-user",
                "ABC",
                "TOKEN-EXAMPLE-ABCD");
        HttpEntity<User> requestEntity = new HttpEntity<User>(user);

        return restTemplate.exchange(
                "http://127.0.0.1:8080/user",
                HttpMethod.POST,
                requestEntity,
                User.class);
    }
}
```

### 6. Read the log

The following shows the main fields of the resulting call log:

```json
{
  "event": "http_exchange",
  "schemaVersion": 1,
  "durationMs": 18,
  "request": {
    "method": "POST",
    "uri": {
      "full": "http://127.0.0.1:8080/user"
    },
    "body": {
      "username": "example-user",
      "customerCode": "***"
    }
  },
  "response": {
    "status": 200,
    "body": {
      "username": "example-user",
      "customerCode": "***"
    }
  }
}
```

`customerCode` uses `FULL` masking, and `accessToken` is excluded from both request and response log bodies. The User objects sent and returned by the application retain their original values.

## How it works

Log Mask does not proxy every RestTemplate in the application. It installs call logging only on selected instances.

For each selected RestTemplate:

1. An interceptor records the request URI, Headers, response status, and call duration.
2. Existing `HttpMessageConverter` instances continue to perform the actual request serialization and response deserialization.
3. When the original converter reads or writes successfully, Log Mask uses the same Java value to generate a Body representation intended only for logs.
4. When the HTTP call ends, one complete JSON entry is written through `log.mask.http`.

Log Mask does not change converter type checks or real read and write behavior. It does not replace the request factory or configure connection pools, timeouts, TLS, proxies, or retries.

## Select RestTemplate Beans

### No RestTemplate Bean

When the context has no RestTemplate Bean, the Starter creates `logMaskRestTemplate` with the application's `RestTemplateBuilder` and selects it automatically. Production applications should normally declare their own RestTemplate and configure its connection pool, timeouts, TLS, proxy, and retries.

### Existing RestTemplate Beans

An existing Bean can be selected in three ways. The options can be combined, and one instance is instrumented only once:

1. Add `@ObservedRestTemplate` to the Bean.

2. Configure its name:

```yaml
log-mask:
  logging:
    rest-template:
      observed-bean-names:
        - partnerRestTemplate
```

3. Provide a code-based selector:

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot2.RestTemplateObservationConfigurer;
import org.springframework.context.annotation.Bean;

@Bean
public RestTemplateObservationConfigurer partnerSelection() {
    return (beanName, restTemplate) ->
            "partnerRestTemplate".equals(beanName);
}
```

A configured name must exist in the current `ApplicationContext` and refer to a RestTemplate. A missing name or a Bean of another type stops application startup.

Together with the automatically created `logMaskRestTemplate`, these are the four selection paths supported by the Starter.

## Shared logging and masking guide

The following topics are identical in Boot 2 and Boot 3 and are maintained in the [RestTemplate common guide](../log-mask-resttemplate/README.md):

| Topic | Guide |
| --- | --- |
| Log keys and states | [Log structure](../log-mask-resttemplate/README.md#log-structure) |
| Logger and appenders | [Log output](../log-mask-resttemplate/README.md#log-output) |
| Path switches and rules | [Path](../log-mask-resttemplate/README.md#path) |
| Query switches and rules | [Query](../log-mask-resttemplate/README.md#query) |
| Request Header switches and rules | [Request Header](../log-mask-resttemplate/README.md#request-header) |
| Request Body switches and rules | [Request Body](../log-mask-resttemplate/README.md#request-body) |
| Response Header switches and rules | [Response Header](../log-mask-resttemplate/README.md#response-header) |
| Response Body switches and rules | [Response Body](../log-mask-resttemplate/README.md#response-body) |
| Trace ID and custom strategies | [Trace ID](../log-mask-resttemplate/README.md#trace-id) |
| Call-log troubleshooting | [FAQ](../log-mask-resttemplate/README.md#faq) |

## Spring Boot compatibility

This Starter is for Spring Boot 2 only. For Spring Boot 3, use `log-mask-resttemplate-spring-boot3-starter`. If the wrong generation is used, the application reports a clear error during startup.

## Related documentation

- [Log Mask project home](../README.md)
- [RestTemplate common guide](../log-mask-resttemplate/README.md)
- [Core guide](../log-mask-core/README.md)
- [Spring Boot 2 runnable sample](../log-mask-samples/README.md)
- [Spring Boot 3 Starter guide](../log-mask-resttemplate-spring-boot3-starter/README.md)
- [Security policy](../SECURITY.md)
