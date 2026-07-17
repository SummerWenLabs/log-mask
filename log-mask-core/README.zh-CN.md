# Log Mask Core

[English](README.md) · **简体中文**

> 把 Java 对象中的敏感字段处理后，生成可以直接写入日志的 JSON。

## 为什么使用

1. 写日志前需要脱敏手机号、证件号、银行卡号等敏感字段。
2. 有些字段不能出现在日志中，但业务对象本身还要正常使用。
3. 只需要处理对象中的敏感字段，不希望引入 Spring 或 HTTP 客户端。

## 模块介绍

`log-mask-core` 是 Log Mask 的基础模块，支持 Java 8，不依赖 Spring。它只处理标有
`@Mask` 或 `@LogExclude` 的字段，未标记字段按 Jackson 的配置原样输出。

字段处理只作用于日志 JSON，不会修改原对象，也不会修改应用传入的
`ObjectMapper`。

## 核心特性

- 使用 `@Mask` 脱敏字段。
- 使用 `@LogExclude` 从日志 JSON 中删除字段。
- 支持内置脱敏类型、正则替换和自定义策略。
- 嵌套对象、集合、数组和 Map 中的注解自动生效。
- 可以限制单次输出的 UTF-8 字节数。
- `LogMasker` 构建后可以在线程间复用。

## 快速开始

### Maven

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 1. 标记字段

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

### 2. 生成日志 JSON

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.LogMasker;

LogMasker logMasker = LogMasker.builder(new ObjectMapper()).build();

LoginEvent event = new LoginEvent(
        "13800138000", "actual-token", "SUCCESS");
String logJson = logMasker.mask(event);
```

原对象的数据：

```json
{"phone":"13800138000","accessToken":"actual-token","result":"SUCCESS"}
```

`logJson`：

```json
{"phone":"138****8000","result":"SUCCESS"}
```

`phone` 已脱敏，`accessToken` 已删除，未标记的 `result` 保留原值。

## 字段脱敏

`@Mask` 可以写在字段或 getter 上。

| 写法 | 原值 | 日志结果 |
| --- | --- | --- |
| `@Mask(type = MaskType.REDACT)` | 任意值 | `"<redacted>"` |
| `@Mask(type = MaskType.PHONE)` | `13800138000` | `"138****8000"` |
| `@Mask(type = MaskType.EMAIL)` | `alice@example.com` | `"a****@example.com"` |
| `@Mask(type = MaskType.ID_CARD)` | `110101199001011234` | `"110101********1234"` |
| `@Mask(type = MaskType.BANK_CARD)` | `6222021234567890` | `"6222********7890"` |
| `@Mask(type = MaskType.FULL)` | `A😀中` | `"***"` |
| `@Mask(type = MaskType.CUSTOM, pattern = "[0-9]", replacement = "*")` | `AB-12-34` | `"AB-**-**"` |

PHONE、EMAIL、ID_CARD 和 BANK_CARD 遇到格式不符合要求的非空值时，会输出等长
`*`，不会保留原值。`null` 仍是 `null`，空字符串仍是空字符串。

嵌套对象、集合、数组和 Map 中的字段使用相同注解即可，不需要开启额外选项。

## 排除字段

不允许进入日志的字段使用 `@LogExclude`：

```java
@LogExclude
public final String accessToken;
```

处理前：

```json
{"accessToken":"actual-token","result":"SUCCESS"}
```

日志结果：

```json
{"result":"SUCCESS"}
```

字段名和值都会从日志中删除。同一属性同时标有 `@LogExclude` 和 `@Mask` 时，该属性
不会出现在日志中。

## 自定义策略

先实现 `MaskTypeDefinition` 接口：

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

注册策略，并在字段上使用相同的 `typeCode`：

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

`typeCode` 区分大小写。自定义策略返回 `null` 或抛出异常时，当前字段输出
`"<redacted>"`，其他字段继续生成。

## 限制输出大小

不希望单条 JSON 过大时，传入允许的最大 UTF-8 字节数：

```java
import io.github.summerwenlabs.log.mask.BoundedMaskResult;

BoundedMaskResult result = logMasker.mask(event, 64);

if (!result.isLimitExceeded()) {
    String logJson = result.getJson();
}
```

1. `64` 表示最终 JSON 最多允许 64 个 UTF-8 字节，最小值是 1。
2. 未超限时，`getJson()` 返回完整 JSON。
3. 超限时不会返回截断内容；此时调用 `getJson()` 会抛出
   `IllegalStateException`。

`mask(value)` 不限制大小，始终尝试返回完整 JSON。

## 关闭字段处理

`governanceEnabled(false)` 会关闭 `@Mask`、`@LogExclude` 和全部自定义策略：

```java
LogMasker rawMasker = LogMasker.builder(new ObjectMapper())
        .governanceEnabled(false)
        .build();

String logJson = rawMasker.mask(event);
```

日志结果：

```json
{"phone":"13800138000","accessToken":"actual-token","result":"SUCCESS"}
```

敏感值会按 Jackson 配置原样输出，只有明确需要原始日志时才关闭字段处理。

## 方法参数与异常

| 方法 | 用途 |
| --- | --- |
| `LogMasker.builder(objectMapper)` | 使用应用现有的 Jackson 配置创建 builder |
| `mask(value)` | 返回完整 JSON，不限制大小 |
| `mask(value, maxUtf8Bytes)` | 限制最终 JSON 的 UTF-8 字节数 |
| `mask(value, declaredType, maxUtf8Bytes)` | 调用方已经持有完整 `Type` 时使用，例如 `List<LoginEvent>` |

普通业务代码使用前两个 `mask` 方法即可。如果代码已经持有完整的泛型 `Type`，可以
在限制大小的同时把这个类型传给 `LogMasker`：

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

`value` 可以是任何 Jackson 能序列化的值。对象整体无法生成 JSON 时，`mask(...)`
抛出 `LogMaskException`，原始异常保留在 `cause` 链中。
