# Log Mask

**English** | [简体中文](README.zh-CN.md)

A logging enhancement component for Spring Boot applications, with business
object masking and secure HTTP call logging.

It changes only log output. Real business data remains untouched.

<p align="center">
  <a href="#project-overview">Project overview</a> |
  <a href="#quick-start">Quick start</a> |
  <a href="#documentation">Documentation</a>
</p>

## Project overview

Log Mask helps Spring Boot applications keep useful diagnostic logs while
protecting sensitive data.

It provides:

- Business object masking
- RestTemplate request and response logging
- Masking controls for path, query, header, and body data
- Built-in mask types and custom masking strategies
- Independent controls for request and response log content

It is intended for:

- Spring Boot 2 and Spring Boot 3 applications
- Applications that call external HTTP services with RestTemplate
- Projects that need consistent log-masking rules
- Systems whose production logs are subject to security audits

## Why this project exists

Logs are essential for troubleshooting, but business objects, HTTP requests,
and HTTP responses often contain sensitive data such as:

- Phone numbers
- Identity numbers
- Email addresses
- Bank card numbers
- Tokens
- Request parameters and response data

Without masking, logs may contain values like these fictional examples:

```json
{
  "username": "Example User",
  "phone": "138ABCDABCD",
  "idCard": "1101ABCD1990EFGH12",
  "bankCard": "6222ABCD5678EFGH"
}
```

This can result in:

- Sensitive data exposure
- Logs that do not meet security audit requirements
- Repeated masking implementations across projects
- Insufficient diagnostic evidence when call logs are removed entirely

Log Mask simplifies this tradeoff by retaining useful diagnostic information
while producing logs suitable for security audits.

# Core capabilities

## 1. Business object masking

Log Mask converts business objects into masked JSON for logging. It does not
change the original field values or affect normal object serialization.

Use `@Mask` to mark fields that must be masked and `@LogExclude` to omit fields
that must never be written to logs.

Supported inputs and rules include:

- Java Beans and nested objects
- Business objects in collections such as `List<User>`, arrays such as
  `User[]`, and maps such as `Map<String, User>`
- Built-in rules for phone numbers, email addresses, identity numbers, bank
  card numbers, and other common values
- Regular-expression replacement
- Custom masking strategies

See the [Core guide](./log-mask-core/README.md) for details.

## 2. RestTemplate call logging

Once enabled for a RestTemplate, Log Mask writes one complete JSON event for
each call, including its request, response, and duration.

Request data includes:

- HTTP method, always recorded while call logging is enabled
- URL, always recorded while call logging is enabled
- Path parameters, recorded as part of the URL and not independently disabled
- Query parameters, recorded as part of the URL; individual parameters can be
  masked or excluded
- Request headers, enabled by default and configurable
- Request body, enabled by default and configurable

Response data includes:

- HTTP status, always recorded when a response is available
- Response headers, enabled by default and configurable
- Response body, enabled by default and configurable

Each event also includes:

- Call time
- Call ID
- Call duration
- Trace ID, read by default and recorded as `null` when no configured MDC value
  is available

Path, query, header, and body masking rules are configured independently.
Request and response body log sizes can also be limited. These settings affect
only log content; real requests, responses, and business exceptions are not
changed.

See the [Spring Boot 2 RestTemplate Starter guide](./log-mask-resttemplate-spring-boot2-starter/README.md)
or the [Spring Boot 3 RestTemplate Starter guide](./log-mask-resttemplate-spring-boot3-starter/README.md).

# Modules

Choose the Maven artifact that matches the application and its Spring Boot
generation.

| Maven module | Purpose | Spring Boot | Minimum Java version |
| --- | --- | --- | --- |
| `log-mask-core` | Business object masking; can be used independently | No Spring Boot dependency | Java 8 |
| `log-mask-resttemplate-spring-boot2-starter` | RestTemplate request and response logging for Spring Boot 2 | 2.6.15, 2.7.18 | Java 8 |
| `log-mask-resttemplate-spring-boot3-starter` | RestTemplate request and response logging for Spring Boot 3 | 3.2.12, 3.3.13, 3.4.13, 3.5.16 | Java 17 |

Both RestTemplate Starters include Core and their matching autoconfigure module.
Do not add Core separately when using a Starter, and do not add the Boot 2 and
Boot 3 Starters to the same application.

# Quick start

## Maven dependencies

For business object masking only, add Core:

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

For RestTemplate call logging in a Spring Boot 2 application, add:

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

For RestTemplate call logging in a Spring Boot 3 application, add:

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-resttemplate-spring-boot3-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Each RestTemplate Starter already includes Core.

## Mask a business object

First, mark fields that must be masked or excluded:

```java
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;

final class LoginEvent {

    @Mask(type = MaskType.EMAIL)
    public final String email;

    @LogExclude
    public final String accessToken;

    public final String result;

    LoginEvent(String email, String accessToken, String result) {
        this.email = email;
        this.accessToken = accessToken;
        this.result = result;
    }
}
```

Generate JSON for the log:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.LogMasker;

LogMasker logMasker = LogMasker.builder(new ObjectMapper()).build();

LoginEvent event = new LoginEvent(
        "demo@example.com",
        "TOKEN-EXAMPLE-ABCD",
        "SUCCESS");

String logJson = logMasker.mask(event);
```

`logJson` contains:

```json
{"email":"d***@example.com","result":"SUCCESS"}
```

The email is masked, the access token is excluded, and the unmarked result
keeps its original value. The source `LoginEvent` is not modified.

## Log RestTemplate calls

This Spring Boot 3 example selects a RestTemplate with
`@ObservedRestTemplate`:

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot3.ObservedRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Bean
@ObservedRestTemplate
public RestTemplate partnerRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

Spring Boot 2 uses the annotation with the same simple name from this package:

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
```

Enable INFO output for `log.mask.http`:

```yaml
logging:
  level:
    log.mask.http: INFO
```

Assume `User.email` has `@Mask(type = MaskType.EMAIL)` and
`User.accessToken` has `@LogExclude`:

```java
User user = new User(
        "Example User",
        "demo@example.com",
        "TOKEN-EXAMPLE-ABCD");

HttpEntity<User> requestEntity = new HttpEntity<User>(user);

ResponseEntity<User> response = restTemplate.exchange(
        "http://127.0.0.1:8080/user",
        HttpMethod.POST,
        requestEntity,
        User.class);
```

For readability, this example shows only the main event fields:

```json
{
  "event": "http_exchange",
  "durationMs": 18,
  "request": {
    "method": "POST",
    "uri": {
      "full": "http://127.0.0.1:8080/user"
    },
    "body": {
      "username": "Example User",
      "email": "d***@example.com"
    }
  },
  "response": {
    "status": 200,
    "body": {
      "username": "Example User",
      "email": "d***@example.com"
    }
  }
}
```

The complete event also contains its timestamp, call ID, trace ID, processing
state for each region, and headers. The access token is absent from request and
response logs, but it remains unchanged in the actual data sent and received.

Only RestTemplate beans selected with `@ObservedRestTemplate` or configuration
are observed. Other RestTemplate instances remain unchanged.

# Documentation

## Core

- [Core guide](./log-mask-core/README.md)

## RestTemplate call logging

- [Spring Boot 2 RestTemplate Starter guide](./log-mask-resttemplate-spring-boot2-starter/README.md)
- [Spring Boot 3 RestTemplate Starter guide](./log-mask-resttemplate-spring-boot3-starter/README.md)

## Runnable samples

- [Spring Boot 2 samples](./log-mask-samples/README.md)
- [Spring Boot 3 sample](./log-mask-samples-spring-boot3/README.md)

## Project resources

- [Benchmarks](./log-mask-benchmarks/README.md)
- [Security policy](./SECURITY.md)
- [Contributing](./CONTRIBUTING.md)
- [Apache License 2.0](./LICENSE)

# Design principles

## Start simply, extend when needed

Common fields can use built-in mask types, while application-specific cases
can use custom masking strategies. Spring Boot applications can add a Starter
to enable RestTemplate call logging.

## Logs only

Log Mask generates content for logs. It does not modify business objects, real
HTTP requests, responses, RestTemplate configuration, or business exceptions.

## Explicit and controllable rules

Annotations and configuration determine which values are masked, excluded, or
retained. Values without a rule are logged unchanged, so all sensitive data
must be configured before production use.

## Modular design

Core handles business object masking. Each RestTemplate Starter handles HTTP
request and response logging for its Spring Boot generation. Applications can
depend only on the modules they need.

# Roadmap

# Contributing

Issues and pull requests are welcome. Read the [contribution guide](./CONTRIBUTING.md)
before making changes.

# License

This project is licensed under the [Apache License 2.0](./LICENSE).
