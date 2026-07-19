# log-mask-core

[English](README.md) | **简体中文**

`log-mask-core` 是 Log Mask 的基础模块，支持 Java 8，不依赖 Spring，可以独立使用。

它根据业务对象字段上的规则生成可直接写入日志的 JSON。未标记的字段按 Jackson 配置保留原值，原始业务对象和应用传入的 `ObjectMapper` 都不会被修改。

## 核心特性

- 使用 `@Mask` 脱敏字段
- 使用 `@LogExclude` 从日志中排除字段
- 内置常用脱敏规则，且支持正则替换和自定义脱敏策略
- 支持嵌套对象，以及 `List<User>`、`User[]` 和 `Map<String, User>` 中的业务对象
- 支持限制最终 JSON 的 UTF-8 字节数，超限时不会返回截断内容
- `LogMasker` 构建后可以在多个线程中复用

# 快速开始

## Maven

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 1. 添加字段规则

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

## 2. 创建 LogMasker

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.summerwenlabs.log.mask.LogMasker;

LogMasker logMasker = LogMasker.builder(new ObjectMapper()).build();
```

`LogMasker` 构建后可以被多个线程安全复用，不需要每次打印日志时重新创建。

## 3. 生成日志 JSON

```java
LoginEvent event = new LoginEvent(
        "demo@example.com",
        "TOKEN-EXAMPLE-ABCD",
        "SUCCESS");

String logJson = logMasker.mask(event);
```

输出：

```json
{
  "email": "d***@example.com",
  "result": "SUCCESS"
}
```

处理结果：

| 字段 | 结果 |
| --- | --- |
| `email` | 按 EMAIL 规则脱敏 |
| `accessToken` | 从日志中排除 |
| `result` | 保留原值 |

原始 `LoginEvent` 不会被修改。

# 字段规则

## @Mask

`@Mask` 用于标记需要脱敏的字段，可以写在字段或 getter 上。

它支持三种写法：

| 写法 | 用途 |
| --- | --- |
| `@Mask(type = MaskType.EMAIL)` | 使用内置脱敏类型 |
| `@Mask(type = MaskType.CUSTOM, pattern = "...", replacement = "...")` | 使用当前字段声明的正则替换 |
| `@Mask(typeCode = "CUSTOMER_ID")` | 使用已注册的自定义脱敏策略 |

三种写法不能混合使用。配置冲突、正则错误或找不到自定义策略时，当前字段会输出 `"<redacted>"`，其他字段继续正常生成。

除 REDACT 外，PHONE、EMAIL、ID_CARD、BANK_CARD、FULL、正则替换和自定义脱敏策略都用于处理 String 字段。标记在非 String 字段上时，字段会输出 `"<redacted>"`，不会调用内容脱敏策略。

## @LogExclude

`@LogExclude` 用于排除不允许写入日志的字段，可以写在字段或 getter 上。

```java
@LogExclude
public final String accessToken;
```

生成日志 JSON 时，字段名和值都会被删除。`@LogExclude` 不会读取字段值；同一个属性同时存在 `@LogExclude` 和 `@Mask` 时，以 `@LogExclude` 为准。

## 未标记字段

没有添加 `@Mask` 或 `@LogExclude` 的字段会按照 Jackson 配置保留原值。

# 内置脱敏类型

`@Mask` 支持以下内置类型：

| 类型 | 处理规则 | 日志结果示意 |
| --- | --- | --- |
| `MaskType.REDACT` | 不读取原值，直接使用固定内容替代 | `"<redacted>"` |
| `MaskType.PHONE` | 处理 11 位手机号，保留前 3 位和后 4 位 | `前三位****后四位` |
| `MaskType.EMAIL` | 保留邮箱首字符和域名 | `demo@example.com` → `d***@example.com` |
| `MaskType.ID_CARD` | 处理 18 位身份证号，保留前 6 位和后 4 位 | `前六位********后四位` |
| `MaskType.BANK_CARD` | 处理 12 至 19 位银行卡号，保留前 4 位和后 4 位 | `前四位********后四位` |
| `MaskType.FULL` | 每个 Unicode 字符替换为一个 `*` | `A😀中` → `***` |

示例：

```java
@Mask(type = MaskType.EMAIL)
public final String email;
```

PHONE、EMAIL、ID_CARD 和 BANK_CARD 遇到格式不符合要求的非空值时，会输出相同字符数量的 `*`，不会保留原值。

`null` 保持为 `null`，空字符串保持为空字符串。

# 正则替换

当内置类型不能满足需求时，可以直接在字段上声明正则表达式和替换内容：

```java
@Mask(
        type = MaskType.CUSTOM,
        pattern = "[0-9]",
        replacement = "*")
public final String referenceCode;
```

原值：

```text
AB-12-34
```

日志结果：

```text
AB-**-**
```

`pattern` 使用 Java 正则表达式，`replacement` 是替换内容。

使用正则替换时必须设置 `type = MaskType.CUSTOM`，并且不能同时设置 `typeCode`。正则表达式无效时，字段会输出 `"<redacted>"`。

# 自定义脱敏策略

当内置类型和正则替换无法满足需求时，可以注册自定义脱敏策略。

## 1. 定义脱敏策略

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

## 2. 注册脱敏策略

```java
import java.util.Collections;
import io.github.summerwenlabs.log.mask.strategy.MaskStrategyRegistry;

MaskStrategyRegistry registry = MaskStrategyRegistry.of(
        Collections.singletonList(new CustomerIdMask()));

LogMasker customMasker = LogMasker.builder(new ObjectMapper())
        .strategyRegistry(registry)
        .build();
```

## 3. 标记业务字段

```java
final class CustomerRecord {

    @Mask(typeCode = "CUSTOMER_ID")
    public final String customerId;

    CustomerRecord(String customerId) {
        this.customerId = customerId;
    }
}
```

## 4. 生成日志 JSON

```java
CustomerRecord record =
        new CustomerRecord("CUSTOMER-EXAMPLE-ABCD");

String logJson = customMasker.mask(record);
```

输出：

```json
{"customerId":"<customer-id>"}
```

自定义策略需要遵守以下规则：

- `typeCode` 不能为空，首尾不能包含空白，并且区分大小写
- 多个策略不能使用相同的 `typeCode`
- 自定义策略可能被多个线程同时调用，实现类必须线程安全
- `mask(...)` 返回 `null` 或抛出异常时，当前字段输出 `"<redacted>"`，其他字段继续正常生成

# 嵌套对象与容器

业务对象中的字段规则会继续应用到嵌套对象。

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

生成日志 JSON：

```java
LoginRequest request =
        new LoginRequest(new User("demo@example.com"));

String logJson = logMasker.mask(request);
```

输出：

```json
{
  "user": {
    "email": "d***@example.com"
  }
}
```

同样支持集合、数组和 Map 中的业务对象：

```java
List<User> userList;
User[] userArray;
Map<String, User> userMap;
```

# Jackson 配置

`log-mask-core` 使用 Jackson 生成日志 JSON。创建 `LogMasker` 时可以传入应用已经配置好的 `ObjectMapper`：

```java
LogMasker logMasker = LogMasker.builder(objectMapper).build();
```

`LogMasker` 会复制这个 `ObjectMapper`，并保留其中已经配置的内容，例如：

- Jackson Module
- 字段命名规则
- 日期格式
- 字段可见性
- JSON 序列化配置

应用传入的 `ObjectMapper` 不会被修改。

Spring Boot 应用建议在系统初始化时创建一个业务对象日志专用的单例 Bean：

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

业务代码通过依赖注入复用这个 Bean。

# 限制输出大小

不希望单条日志 JSON 过大时，可以设置允许的最大 UTF-8 字节数：

```java
import io.github.summerwenlabs.log.mask.BoundedMaskResult;

BoundedMaskResult result = logMasker.mask(event, 64);

if (!result.isLimitExceeded()) {
    String logJson = result.getJson();
}
```

`64` 表示最终 JSON 最多允许 64 个 UTF-8 字节。

处理结果：

| 状态 | `isLimitExceeded()` | `getJson()` |
| --- | --- | --- |
| 未超限 | `false` | 返回完整 JSON |
| 超限 | `true` | 抛出 `IllegalStateException` |

超限时不会返回截断后的 JSON，避免产生不完整或无效的日志内容。

最大字节数不能小于 1。`mask(value)` 不限制大小，会尝试返回完整 JSON。

# 关闭字段处理

通过 `governanceEnabled(false)` 可以关闭所有字段脱敏和排除规则：

```java
LogMasker rawMasker = LogMasker.builder(new ObjectMapper())
        .governanceEnabled(false)
        .build();

String logJson = rawMasker.mask(event);
```

输出：

```json
{
  "email": "demo@example.com",
  "accessToken": "TOKEN-EXAMPLE-ABCD",
  "result": "SUCCESS"
}
```

关闭后，`@Mask`、`@LogExclude` 和自定义脱敏策略都不会执行，字段会按照 Jackson 配置正常输出。

只有明确需要查看原始日志内容时才应关闭字段处理。

# 方法参数与异常

## 主要方法

| 方法 | 用途 |
| --- | --- |
| `LogMasker.builder(objectMapper)` | 使用指定的 Jackson 配置创建 `LogMasker` |
| `mask(value)` | 生成完整 JSON，不限制大小 |
| `mask(value, maxUtf8Bytes)` | 限制最终 JSON 的 UTF-8 字节数 |
| `mask(value, declaredType, maxUtf8Bytes)` | 使用指定的 Java 类型并限制最终 JSON 大小 |

`value` 可以是任何 Jackson 能够序列化的值。传入 `null` 时返回 JSON `null`。

## 泛型类型

代码已经持有完整泛型类型时，可以将 `Type` 传给 `LogMasker`：

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

## 异常处理

- 最大 UTF-8 字节数小于 1 时抛出 `IllegalArgumentException`
- `declaredType` 为 `null` 时抛出 `NullPointerException`
- 对象整体无法生成 JSON 时抛出 `LogMaskException`
- Jackson 的原始异常会保留在 `LogMaskException` 的 cause 中
- 单个字段的规则配置错误或自定义策略执行失败时，当前字段输出 `"<redacted>"`，其他字段继续正常生成

# 相关文档

- [Log Mask 项目主页](../README.zh-CN.md)
- [Spring Boot 2 RestTemplate Starter 使用指南](../log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)
- [Spring Boot 3 RestTemplate Starter 使用指南](../log-mask-resttemplate-spring-boot3-starter/README.zh-CN.md)
- [安全策略](../SECURITY.md)
