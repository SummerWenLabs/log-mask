# Log Mask Core

**English** · [简体中文](README.zh-CN.md)

> Turn sensitive fields in Java objects into JSON that can be written directly to logs.

## Why use it

1. Sensitive fields such as phone numbers, identity numbers, and bank cards
   need masking before an object is logged.
2. Some fields must stay on the business object but must not appear in logs.
3. Sensitive fields need processing without adding Spring or an HTTP client.

## Module overview

`log-mask-core` is the base Log Mask module. It supports Java 8 and has no
Spring dependency. Only fields marked with `@Mask` or `@LogExclude` are
processed; unmarked fields are written according to the Jackson configuration.

Field processing changes only the log JSON. It does not modify the source
object or the application's `ObjectMapper`.

## Features

- Mask fields with `@Mask`.
- Remove fields from log JSON with `@LogExclude`.
- Use built-in mask types, regular expressions, or custom strategies.
- Apply annotations automatically inside nested objects, collections, arrays,
  and maps.
- Limit the UTF-8 size of one JSON result.
- Reuse a built `LogMasker` across threads.

## Quick start

### Maven

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 1. Mark the fields

```java
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;

final class LoginEvent {
    @Mask(type = MaskType.PHONE)
    public final String phone;

    @LogExclude
    public final String accessToken;

    public final String result;

    LoginEvent(String phone, String accessToken, String result) {
        this.phone = phone;
        this.accessToken = accessToken;
        this.result = result;
    }
}
```

### 2. Generate log JSON

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.LogMasker;

LogMasker logMasker = LogMasker.builder(new ObjectMapper()).build();

LoginEvent event = new LoginEvent(
        "13800138000", "actual-token", "SUCCESS");
String logJson = logMasker.mask(event);
```

Source object data:

```json
{"phone":"13800138000","accessToken":"actual-token","result":"SUCCESS"}
```

`logJson`:

```json
{"phone":"138****8000","result":"SUCCESS"}
```

`phone` is masked, `accessToken` is removed, and the unmarked `result` keeps its
original value.

## Mask fields

`@Mask` can be placed on a field or getter.

| Declaration | Source value | Log result |
| --- | --- | --- |
| `@Mask(type = MaskType.REDACT)` | Any value | `"<redacted>"` |
| `@Mask(type = MaskType.PHONE)` | `13800138000` | `"138****8000"` |
| `@Mask(type = MaskType.EMAIL)` | `alice@example.com` | `"a****@example.com"` |
| `@Mask(type = MaskType.ID_CARD)` | `110101199001011234` | `"110101********1234"` |
| `@Mask(type = MaskType.BANK_CARD)` | `6222021234567890` | `"6222********7890"` |
| `@Mask(type = MaskType.FULL)` | `A😀中` | `"***"` |
| `@Mask(type = MaskType.CUSTOM, pattern = "[0-9]", replacement = "*")` | `AB-12-34` | `"AB-**-**"` |

For PHONE, EMAIL, ID_CARD, and BANK_CARD, a malformed non-empty value becomes
the same number of `*` characters instead of remaining visible. `null` stays
`null`, and an empty string stays empty.

The same annotations work in nested objects, collections, arrays, and maps. No
additional option is required.

## Exclude fields

Use `@LogExclude` for a field that must not enter the log:

```java
@LogExclude
public final String accessToken;
```

Before processing:

```json
{"accessToken":"actual-token","result":"SUCCESS"}
```

Log result:

```json
{"result":"SUCCESS"}
```

Both the field name and value are removed. If one property has both
`@LogExclude` and `@Mask`, that property does not appear in the log.

## Custom strategies

First implement the `MaskTypeDefinition` interface:

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

Register the strategy and use the same `typeCode` on the field:

```java
import java.util.Collections;

import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;

MaskStrategyRegistry registry = MaskStrategyRegistry.of(
        Collections.singletonList(new CustomerIdMask()));

LogMasker customMasker = LogMasker.builder(new ObjectMapper())
        .strategyRegistry(registry)
        .build();

final class CustomerRecord {
    @Mask(typeCode = "CUSTOMER_ID")
    public final String id;

    CustomerRecord(String id) {
        this.id = id;
    }
}

String logJson = customMasker.mask(new CustomerRecord("customer-42"));
// {"id":"<customer-id>"}
```

`typeCode` is case-sensitive. If a custom strategy returns `null` or throws an
exception, that field becomes `"<redacted>"` and the remaining fields continue.

## Limit output size

Pass the maximum permitted UTF-8 size when one JSON result must stay small:

```java
import io.github.summerwenlabs.log.mask.BoundedMaskResult;

BoundedMaskResult result = logMasker.mask(event, 64);

if (!result.isLimitExceeded()) {
    String logJson = result.getJson();
}
```

1. `64` allows at most 64 UTF-8 bytes in the final JSON. The minimum is 1.
2. When the result fits, `getJson()` returns the complete JSON document.
3. When it does not fit, no truncated JSON is returned. Calling `getJson()` on
   that result throws `IllegalStateException`.

`mask(value)` has no size limit and always attempts to return the complete JSON.

## Disable field processing

`governanceEnabled(false)` disables `@Mask`, `@LogExclude`, and all custom
strategies:

```java
LogMasker rawMasker = LogMasker.builder(new ObjectMapper())
        .governanceEnabled(false)
        .build();

String logJson = rawMasker.mask(event);
```

Log result:

```json
{"phone":"13800138000","accessToken":"actual-token","result":"SUCCESS"}
```

Sensitive values are then written according to the Jackson configuration. Turn
off field processing only when raw log output is intentional.

## Method parameters and failures

| Method | Use |
| --- | --- |
| `LogMasker.builder(objectMapper)` | Start with the application's Jackson configuration |
| `mask(value)` | Return complete JSON with no size limit |
| `mask(value, maxUtf8Bytes)` | Limit the final JSON by its UTF-8 size |
| `mask(value, declaredType, maxUtf8Bytes)` | Use when the caller already has a complete `Type`, such as `List<LoginEvent>` |

Ordinary application code can use the first two `mask` methods. If code already
has the complete generic `Type`, it can pass that type while limiting the output
size:

```java
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

List<LoginEvent> events = Collections.singletonList(event);
Type declaredType = new TypeReference<List<LoginEvent>>() { }.getType();
BoundedMaskResult result =
        logMasker.mask(events, declaredType, 64 * 1024);
```

`value` can be any value Jackson can serialize. If a complete JSON document
cannot be generated, `mask(...)` throws `LogMaskException`; the original
exception remains in the cause chain.
