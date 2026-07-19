# Log Mask

[English](README.md) | **简体中文**

一个面向 Spring Boot 应用的日志增强组件，提供业务对象脱敏以及 HTTP 请求日志安全控制能力。

只打印日志，不修改真实业务数据。

<p align="center">
  <a href="#项目定位">项目定位</a> |
  <a href="#快速开始">快速开始</a> |
  <a href="#文档导航">文档导航</a>
</p>

## 项目定位

`Log Mask` 是一个用于解决企业应用日志安全问题的 Spring Boot 日志增强组件。

它提供：

- 业务对象日志脱敏
- RestTemplate 请求与响应日志记录
- Path、Query、Header 和 Body 脱敏控制
- 内置脱敏规则，且支持自定义脱敏策略
- 可控的请求与响应日志输出

适用于：

- Spring Boot 2 和 Spring Boot 3 应用
- 使用 RestTemplate 调用外部 HTTP 服务的应用
- 需要统一管理日志脱敏规则的项目
- 对生产日志安全审计有要求的系统

## 为什么创建这个项目

在实际业务中，日志是排查问题的重要依据，但业务对象、HTTP 请求和响应中经常包含敏感信息，例如：

- 手机号码
- 身份证号码
- 邮箱地址
- 银行卡号
- Token
- 请求参数和响应数据

直接记录这些内容，日志中可能出现以下虚构数据：

```json
{
  "username": "示例用户",
  "phone": "138ABCDABCD",
  "idCard": "1101ABCD1990EFGH12",
  "bankCard": "6222ABCD5678EFGH"
}
```

这会带来：

- 敏感信息泄露风险
- 日志不符合安全审计要求
- 不同项目重复开发和维护脱敏逻辑
- 删除日志后缺少必要的排查依据

Log Mask 希望简化这一过程：保留必要的排查信息，同时打印符合安全审计要求的日志。

# 核心能力

## 1. 业务对象日志脱敏

Log Mask 可以将业务对象转换为脱敏后的日志 JSON，不会修改原始对象中的字段值，也不会影响对象的正常序列化。

开发者可以使用 `@Mask` 声明脱敏字段，使用 `@LogExclude` 排除不应写入日志的字段。

支持：

- Java Bean 及其嵌套对象
- 集合（如 `List<User>`）、数组（如 `User[]`）和 Map（如 `Map<String, User>`）中的业务对象
- 手机号码、邮箱地址、身份证号码、银行卡号等内置脱敏规则
- 正则替换
- 自定义脱敏策略

详细说明见 [Core 使用指南](./log-mask-core/README.zh-CN.md)。

## 2. RestTemplate 请求日志增强

开启后，Log Mask 会为指定的 RestTemplate 调用记录一条完整的 JSON 日志，其中包含请求、响应和耗时信息。

请求日志包含：

- HTTP Method（调用日志开启时始终记录，不可单独关闭）
- URL（调用日志开启时始终记录，不可单独关闭）
- Path 参数（随 URL 记录，不可单独关闭）
- Query 参数（随 URL 记录，不可整体关闭，可以通过规则脱敏或排除指定参数）
- Request Header（默认开启，可以关闭）
- Request Body（默认开启，可以关闭）

响应日志包含：

- HTTP Status（取得响应时始终记录，不可单独关闭）
- Response Header（默认开启，可以关闭）
- Response Body（默认开启，可以关闭）

调用日志还包含：

- 调用时间（始终记录）
- 调用标识（始终记录）
- 调用耗时（始终记录）
- Trace ID（默认读取，可以关闭；MDC 中没有对应值时记录为 `null`）

Log Mask 支持对 Path、Query、Header 和 Body 分别配置脱敏规则，也可以限制请求与响应 Body 的日志大小。所有配置只影响日志内容，不改变实际请求、响应和业务异常。

详细说明见 [Spring Boot 2 RestTemplate Starter 指南](./log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md) 和 [Spring Boot 3 RestTemplate Starter 指南](./log-mask-resttemplate-spring-boot3-starter/README.zh-CN.md)。

# 模块说明

Log Mask 采用模块化设计，应用可以根据使用场景选择对应的 Maven 依赖。

| Maven 模块 | 说明 | Spring Boot | 最低 Java 版本 |
| --- | --- | --- | --- |
| `log-mask-core` | 业务对象日志脱敏，可独立使用 | 不依赖 Spring Boot | Java 8 |
| `log-mask-resttemplate-spring-boot2-starter` | Spring Boot 2 RestTemplate 请求与响应日志 | 2.6.15、2.7.18 | Java 8 |
| `log-mask-resttemplate-spring-boot3-starter` | Spring Boot 3 RestTemplate 请求与响应日志 | 3.2.12、3.3.13、3.4.13、3.5.16 | Java 17 |

两个 RestTemplate Starter 都会自动引入 Core 和对应的 autoconfigure 模块，应用不需要重复添加这些依赖。请选择与应用 Spring Boot 版本匹配的 Starter，不要同时引入 Boot 2 和 Boot 3 Starter。

# 快速开始

## Maven 引入

只需要处理业务对象日志时，引入 Core：

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

Spring Boot 2 应用需要记录 RestTemplate 请求与响应日志时，引入：

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Spring Boot 3 应用需要记录 RestTemplate 请求与响应日志时，引入：

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-resttemplate-spring-boot3-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

RestTemplate Starter 已经包含 Core，使用 Starter 时不需要再单独引入 `log-mask-core`。

## 对象日志脱敏

先在需要处理的字段上添加脱敏或排除标记：

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

生成用于打印日志的 JSON：

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

`logJson` 的内容：

```json
{"email":"d***@example.com","result":"SUCCESS"}
```

`email` 已脱敏，`accessToken` 已从日志中排除，未标记的 `result` 保留原值。原始 `LoginEvent` 不会被修改。

## RestTemplate 请求日志

下面以 Spring Boot 3 为例，使用 `@ObservedRestTemplate` 标记需要记录日志的 RestTemplate：

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

Spring Boot 2 使用同名注解，导入路径改为：

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot2.ObservedRestTemplate;
```

开启 `log.mask.http` 的 INFO 日志：

```yaml
logging:
  level:
    log.mask.http: INFO
```

下面假设 `User` 的 `email` 使用了 `@Mask(type = MaskType.EMAIL)`，`accessToken` 使用了 `@LogExclude`：

```java
User user = new User(
        "示例用户",
        "demo@example.com",
        "TOKEN-EXAMPLE-ABCD");

HttpEntity<User> requestEntity = new HttpEntity<User>(user);

ResponseEntity<User> response = restTemplate.exchange(
        "http://127.0.0.1:8080/user",
        HttpMethod.POST,
        requestEntity,
        User.class);
```

为了便于阅读，根 README 只展示日志中的主要内容：

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
      "username": "示例用户",
      "email": "d***@example.com"
    }
  },
  "response": {
    "status": 200,
    "body": {
      "username": "示例用户",
      "email": "d***@example.com"
    }
  }
}
```

完整日志还会包含时间、调用标识、Trace ID、各部分处理状态和 Header。`accessToken` 已从请求与响应日志中排除，但实际发送和接收的数据不会被修改。

Log Mask 只处理通过 `@ObservedRestTemplate` 或配置指定的 RestTemplate，不会自动处理应用中的其他 RestTemplate。

# 文档导航

## 基础能力

- [Core 使用指南](./log-mask-core/README.zh-CN.md)

## RestTemplate 请求日志

- [Spring Boot 2 RestTemplate Starter 使用指南](./log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)
- [Spring Boot 3 RestTemplate Starter 使用指南](./log-mask-resttemplate-spring-boot3-starter/README.zh-CN.md)

## 可运行示例

- [Spring Boot 2 示例](./log-mask-samples/README.md)
- [Spring Boot 3 示例](./log-mask-samples-spring-boot3/README.md)

## 项目资料

- [性能基准](./log-mask-benchmarks/README.md)
- [安全策略](./SECURITY.md)
- [贡献说明](./CONTRIBUTING.md)
- [Apache License 2.0](./LICENSE)

# 设计理念

## 简单开始，按需扩展

常见字段可以直接使用内置脱敏规则，特殊业务场景可以增加自定义脱敏策略。Spring Boot 应用可以通过 Starter 快速接入 RestTemplate 请求日志。

## 只处理日志

Log Mask 只生成用于打印的日志内容，不修改业务对象、实际 HTTP 请求、响应、RestTemplate 配置或业务异常。

## 脱敏规则清晰可控

哪些内容需要脱敏、排除或保留，由注解和配置规则决定。没有配置规则的内容会原样记录，生产环境使用前需要确认所有敏感数据都已配置。

## 模块化设计

Core 负责业务对象日志脱敏，RestTemplate Starter 负责对应 Spring Boot 版本的 HTTP 请求与响应日志。应用可以只引入自己需要的模块。

# Roadmap

# 参与贡献

欢迎提交 Issue 和 Pull Request。开始修改前，请先阅读 [贡献说明](./CONTRIBUTING.md)。

# License

本项目使用 [Apache License 2.0](./LICENSE) 开源许可证。
