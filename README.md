# Log Mask

**English** · [简体中文](README.zh-CN.md)

> A Java log-masking library that changes log output without changing business data.

## Why it exists

Log Mask grew out of problems I encountered in real projects:

1. Internal log-security requirements kept getting stricter, so sensitive data
   could no longer be written directly to logs.
2. `RestTemplate` did not provide a ready-to-use call log with the request,
   response, and duration needed for troubleshooting production issues.
3. When each project added its own logs, formats diverged and masking logic had
   to be implemented and maintained repeatedly.
4. Removing call logs during a security review avoided the immediate exposure
   risk but left too little evidence for later troubleshooting.
5. I could not find a ready-made library that was simple to integrate quickly
   and handled both call logging and masking.

## Project overview

Log Mask is a modular Java log-masking project. It can process Java objects
directly or observe `RestTemplate` calls in Spring Boot 2 applications. The
project provides two Maven artifacts for applications:

| Use case | Maven artifact | Runtime |
| --- | --- | --- |
| Convert a Java object to masked JSON for logs | `log-mask-core` | Java 8 |
| Observe Spring Boot 2 `RestTemplate` calls and write call logs | `log-mask-resttemplate-spring-boot2-starter` | Java 8, Spring Boot 2.6.15 or 2.7.18 |

Core has no Spring dependency. The RestTemplate Starter already includes Core,
so applications do not need to add both artifacts.

Log Mask processes only data covered by an explicit annotation or configuration
rule. Unmarked fields, path parameters, query parameters, and HTTP headers are
written to logs unchanged. Configure every sensitive value before using it with
production data.

## Features

- Mask fields with `@Mask` or remove them from logs with `@LogExclude`.
- Apply field rules automatically inside nested objects, collections, arrays,
  and maps.
- Use built-in mask types, regular-expression replacements, or application
  strategies.
- Use Core without Spring and reuse a built `LogMasker` across threads.
- Select a `RestTemplate` with `@ObservedRestTemplate` and record its request,
  response, and duration.
- Change log content without changing business objects, real requests,
  responses, MDC, RestTemplate settings, or business exceptions.

## Quick start

### Mask a Java object

Maven:

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
final class LoginRequest {
    @Mask(type = MaskType.PHONE)
    public final String phone;

    LoginRequest(String phone) {
        this.phone = phone;
    }
}

LogMasker logMasker = LogMasker.builder(new ObjectMapper()).build();
String logJson = logMasker.mask(new LoginRequest("13800138000"));
// {"phone":"138****8000"}
```

See the [Core guide](log-mask-core/README.md) for field rules, custom strategies,
size limits, and failures.

### Observe a RestTemplate

Maven:

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
@Bean
@ObservedRestTemplate
public RestTemplate partnerRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}

final class PartnerRequest {
    @Mask(type = MaskType.PHONE)
    public final String phone;

    PartnerRequest(String phone) {
        this.phone = phone;
    }
}
```

`@ObservedRestTemplate` selects the client to observe. `@Mask` changes only the
request or response object's log content. Bean selection, complete configuration,
HTTP data rules, and startup logging are covered in the
[RestTemplate Starter guide](log-mask-resttemplate-spring-boot2-starter/README.md).

## Documentation

- [Core guide](log-mask-core/README.md)
- [RestTemplate Starter guide](log-mask-resttemplate-spring-boot2-starter/README.md)
- [Runnable samples](log-mask-samples/README.md)
- [Benchmarks](log-mask-benchmarks/README.md)
- [Security policy](SECURITY.md)
- [Contributing](CONTRIBUTING.md)
- [Apache License 2.0](LICENSE)
