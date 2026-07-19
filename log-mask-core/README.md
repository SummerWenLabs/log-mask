# log-mask-core

**English** | [简体中文](README.zh-CN.md)

`log-mask-core` is the base Log Mask module. It supports Java 8, has no Spring
dependency, and can be used independently.

It applies rules declared on business object fields and produces JSON that can
be written directly to logs. Unmarked fields retain their original values
according to the Jackson configuration. Neither the source object nor the
application-provided `ObjectMapper` is modified.

## Features

- Mask fields with `@Mask`
- Exclude fields from logs with `@LogExclude`
- Use built-in mask types, regular-expression replacement, or custom masking
  strategies
- Process nested objects and business objects in `List<User>`, `User[]`, and
  `Map<String, User>`
- Limit the UTF-8 size of the final JSON without returning truncated output
- Reuse a built `LogMasker` across threads

# Quick start

## Maven

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 1. Add field rules

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

## 2. Create a LogMasker

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.LogMasker;

LogMasker logMasker = LogMasker.builder(new ObjectMapper()).build();
```

A built `LogMasker` can be reused safely across threads. It does not need to be
created for every log statement.

## 3. Generate log JSON

```java
LoginEvent event = new LoginEvent(
        "demo@example.com",
        "TOKEN-EXAMPLE-ABCD",
        "SUCCESS");

String logJson = logMasker.mask(event);
```

Output:

```json
{
  "email": "d***@example.com",
  "result": "SUCCESS"
}
```

| Field | Result |
| --- | --- |
| `email` | Masked with the EMAIL rule |
| `accessToken` | Excluded from the log |
| `result` | Retains its original value |

The source `LoginEvent` is not modified.

# Field rules

## @Mask

`@Mask` marks a field for masking and can be placed on a field or getter.

It supports three declaration forms:

| Declaration | Purpose |
| --- | --- |
| `@Mask(type = MaskType.EMAIL)` | Use a built-in mask type |
| `@Mask(type = MaskType.CUSTOM, pattern = "...", replacement = "...")` | Declare regular-expression replacement for this field |
| `@Mask(typeCode = "CUSTOMER_ID")` | Use a registered custom masking strategy |

These forms cannot be combined. If declarations conflict, the regular
expression is invalid, or a custom strategy cannot be found, the current field
is written as `"<redacted>"` while the remaining fields continue normally.

Except for REDACT, PHONE, EMAIL, ID_CARD, BANK_CARD, FULL,
regular-expression replacement, and custom masking strategies operate on
String fields. When one of these rules is placed on a non-String field, that
field is written as `"<redacted>"` and the content-based strategy is not called.

## @LogExclude

`@LogExclude` removes a field that must not be written to logs. It can be placed
on a field or getter.

```java
@LogExclude
public final String accessToken;
```

Both the field name and value are absent from the generated JSON.
`@LogExclude` does not read the field value. If the same property has both
`@LogExclude` and `@Mask`, `@LogExclude` takes precedence.

## Unmarked fields

Fields without `@Mask` or `@LogExclude` retain their original values according
to the Jackson configuration.

# Built-in mask types

`@Mask` supports these built-in types:

| Type | Behavior | Example log result |
| --- | --- | --- |
| `MaskType.REDACT` | Does not read the source value; always uses fixed replacement content | `"<redacted>"` |
| `MaskType.PHONE` | Processes an 11-character phone number and retains the first 3 and last 4 characters | `first 3****last 4` |
| `MaskType.EMAIL` | Retains the first character and domain | `demo@example.com` -> `d***@example.com` |
| `MaskType.ID_CARD` | Processes an 18-character identity number and retains the first 6 and last 4 characters | `first 6********last 4` |
| `MaskType.BANK_CARD` | Processes a 12- to 19-character bank card number and retains the first 4 and last 4 characters | `first 4********last 4` |
| `MaskType.FULL` | Replaces each Unicode code point with one `*` | `A😀中` -> `***` |

Example:

```java
@Mask(type = MaskType.EMAIL)
public final String email;
```

When PHONE, EMAIL, ID_CARD, or BANK_CARD receives a malformed non-empty value,
it returns the same number of `*` characters instead of retaining the source
value.

`null` remains `null`, and an empty string remains empty.

# Regular-expression replacement

When built-in types do not fit the requirement, declare a regular expression
and its replacement directly on the field:

```java
@Mask(
        type = MaskType.CUSTOM,
        pattern = "[0-9]",
        replacement = "*")
public final String referenceCode;
```

Source value:

```text
AB-12-34
```

Log result:

```text
AB-**-**
```

`pattern` uses Java regular-expression syntax, and `replacement` is the
replacement content.

Regular-expression replacement requires `type = MaskType.CUSTOM` and cannot be
combined with `typeCode`. If the expression is invalid, the field is written as
`"<redacted>"`.

# Custom masking strategies

Register a custom masking strategy when neither a built-in type nor
regular-expression replacement meets the requirement.

## 1. Define the strategy

```java
import io.github.summerwenlabs.log.mask.strategy.MaskTypeDefinition;

final class CustomerIdMask implements MaskTypeDefinition {

    @Override
    public String getTypeCode() {
        return "CUSTOMER_ID";
    }

    @Override
    public String mask(String value) {
        return "<customer-id>";
    }
}
```

## 2. Register the strategy

```java
import java.util.Collections;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;

MaskStrategyRegistry registry = MaskStrategyRegistry.of(
        Collections.singletonList(new CustomerIdMask()));

LogMasker customMasker = LogMasker.builder(new ObjectMapper())
        .strategyRegistry(registry)
        .build();
```

## 3. Mark the business field

```java
final class CustomerRecord {

    @Mask(typeCode = "CUSTOMER_ID")
    public final String customerId;

    CustomerRecord(String customerId) {
        this.customerId = customerId;
    }
}
```

## 4. Generate log JSON

```java
CustomerRecord record =
        new CustomerRecord("CUSTOMER-EXAMPLE-ABCD");

String logJson = customMasker.mask(record);
```

Output:

```json
{"customerId":"<customer-id>"}
```

Custom strategies must follow these rules:

- `typeCode` cannot be empty or contain leading or trailing whitespace, and it
  is case-sensitive
- Multiple strategies cannot declare the same `typeCode`
- A custom strategy may be called concurrently and must be thread-safe
- If `mask(...)` returns `null` or throws an exception, the current field is
  written as `"<redacted>"` while the remaining fields continue normally

# Nested objects and containers

Field rules continue to apply inside nested business objects.

```java
final class User {

    @Mask(type = MaskType.EMAIL)
    public final String email;

    User(String email) {
        this.email = email;
    }
}

final class LoginRequest {

    public final User user;

    LoginRequest(User user) {
        this.user = user;
    }
}
```

Generate log JSON:

```java
LoginRequest request =
        new LoginRequest(new User("demo@example.com"));

String logJson = logMasker.mask(request);
```

Output:

```json
{
  "user": {
    "email": "d***@example.com"
  }
}
```

Business objects in collections, arrays, and maps are also supported:

```java
List<User> userList;
User[] userArray;
Map<String, User> userMap;
```

# Jackson configuration

`log-mask-core` uses Jackson to generate log JSON. When creating a `LogMasker`,
you can pass an `ObjectMapper` already configured by the application:

```java
LogMasker logMasker = LogMasker.builder(objectMapper).build();
```

`LogMasker` copies the supplied `ObjectMapper` and retains settings such as:

- Jackson modules
- Property naming rules
- Date formats
- Property visibility
- JSON serialization settings

The application-provided `ObjectMapper` is not modified.

In a Spring Boot application, create one `LogMasker` bean for business object
logging during application initialization:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.LogMasker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LogMaskConfiguration {

    @Bean
    public LogMasker objectLogMasker(ObjectMapper objectMapper) {
        return LogMasker.builder(objectMapper).build();
    }
}
```

Business code can reuse this bean through dependency injection.

# Limit output size

Set the maximum permitted UTF-8 byte count when a single JSON log entry must
remain within a fixed size:

```java
import io.github.summerwenlabs.log.mask.BoundedMaskResult;

BoundedMaskResult result = logMasker.mask(event, 64);

if (!result.isLimitExceeded()) {
    String logJson = result.getJson();
}
```

Here, `64` allows at most 64 UTF-8 bytes in the final JSON.

| Result | `isLimitExceeded()` | `getJson()` |
| --- | --- | --- |
| Within the limit | `false` | Returns the complete JSON |
| Limit exceeded | `true` | Throws `IllegalStateException` |

When the limit is exceeded, no truncated JSON is returned. This avoids writing
incomplete or invalid JSON to logs.

The maximum byte count must be at least 1. `mask(value)` has no size limit and
attempts to return the complete JSON.

# Disable field processing

Use `governanceEnabled(false)` to disable all masking and exclusion rules:

```java
LogMasker rawMasker = LogMasker.builder(new ObjectMapper())
        .governanceEnabled(false)
        .build();

String logJson = rawMasker.mask(event);
```

Output:

```json
{
  "email": "demo@example.com",
  "accessToken": "TOKEN-EXAMPLE-ABCD",
  "result": "SUCCESS"
}
```

When field processing is disabled, `@Mask`, `@LogExclude`, and custom masking
strategies do not run. Fields are serialized normally according to the Jackson
configuration.

Disable field processing only when unmasked log content is explicitly needed.

# Method parameters and exceptions

## Main methods

| Method | Purpose |
| --- | --- |
| `LogMasker.builder(objectMapper)` | Create a `LogMasker` with the specified Jackson configuration |
| `mask(value)` | Generate complete JSON with no size limit |
| `mask(value, maxUtf8Bytes)` | Limit the final JSON by its UTF-8 byte count |
| `mask(value, declaredType, maxUtf8Bytes)` | Use the specified Java type and limit the final JSON size |

`value` can be any value Jackson can serialize. Passing `null` returns the JSON
value `null`.

## Generic types

When code already has the complete generic type, pass its `Type` to
`LogMasker`:

```java
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;

List<LoginEvent> events =
        Collections.singletonList(event);

Type declaredType =
        new TypeReference<List<LoginEvent>>() { }.getType();

BoundedMaskResult result =
        logMasker.mask(events, declaredType, 64 * 1024);
```

## Exceptions

- A maximum UTF-8 byte count below 1 throws `IllegalArgumentException`
- A `null` `declaredType` throws `NullPointerException`
- Failure to generate JSON for the object as a whole throws `LogMaskException`
- The original Jackson exception remains available as the cause of
  `LogMaskException`
- If one field has an invalid rule or its custom strategy fails, only that
  field is written as `"<redacted>"`; the remaining fields continue normally

# Related documentation

- [Log Mask project home](../README.md)
- [Spring Boot 2 RestTemplate Starter guide](../log-mask-resttemplate-spring-boot2-starter/README.md)
- [Spring Boot 3 RestTemplate Starter guide](../log-mask-resttemplate-spring-boot3-starter/README.md)
- [Security policy](../SECURITY.md)
