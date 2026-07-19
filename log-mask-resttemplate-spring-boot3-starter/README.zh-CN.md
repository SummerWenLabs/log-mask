# log-mask-resttemplate-spring-boot3-starter

[English](README.md) | **简体中文**

`log-mask-resttemplate-spring-boot3-starter` 为 Spring Boot 3 应用提供 RestTemplate 请求与响应日志，并按配置对日志内容进行脱敏或排除。

它支持 Java 17 和 21，以及 Spring Boot 3.2.12、3.3.13、3.4.13 和 3.5.16。使用 Spring Boot 2 时，请查看 [Spring Boot 2 Starter 使用指南](../log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)。

## 核心特性

- 只处理通过注解、Bean 名称或代码选择的 RestTemplate
- 每次 HTTP 调用最多打印一条完整 JSON 日志
- 记录请求、响应、耗时和 Trace ID
- 支持 Path、Query、Request Header 和 Response Header 脱敏
- 使用 `@Mask` 和 `@LogExclude` 处理 Request Body 与 Response Body
- Request Header、Request Body、Response Header 和 Response Body 可以分别关闭
- 支持限制每个请求与响应 Body 的日志大小
- 可以与 Spring Boot 3 的 Micrometer Observation 同时使用
- 只处理日志内容，不修改真实请求、响应、业务对象、MDC 或业务异常

日志结构、内容开关和脱敏规则由 Boot 2 与 Boot 3 共用，见 [RestTemplate 通用使用指南](../log-mask-resttemplate/README.zh-CN.md)。

# 快速开始

## 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.summerwenlabs</groupId>
    <artifactId>log-mask-resttemplate-spring-boot3-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Starter 已经包含 Core 和对应的 autoconfigure 模块，不需要重复添加这些依赖。

## 2. 定义请求与响应对象

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

## 3. 选择 RestTemplate

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

`@ObservedRestTemplate` 只影响当前 Bean，应用中的其他 RestTemplate 不会自动开启调用日志。

## 4. 开启调用日志

```yaml
logging:
  level:
    log.mask.http: INFO
```

## 5. 发起请求

下面将上一步定义的 `partnerRestTemplate` 注入业务类，并假设 `http://127.0.0.1:8080/user` 返回相同结构的 User：

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
                "示例用户",
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

## 6. 查看日志

为了便于阅读，下面只展示日志中的主要字段：

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
      "username": "示例用户",
      "customerCode": "***"
    }
  },
  "response": {
    "status": 200,
    "body": {
      "username": "示例用户",
      "customerCode": "***"
    }
  }
}
```

`customerCode` 已执行 `FULL` 脱敏，`accessToken` 已从请求与响应日志中排除。实际发送和接收的 User 数据不会被修改。

# 工作原理

Log Mask 不会代理或处理应用中的所有 RestTemplate。只有被选择的实例才会安装调用日志能力。

对于被选择的 RestTemplate：

1. Interceptor 记录请求 URI、Header、响应状态和调用耗时。
2. 原有 HttpMessageConverter 继续负责真实请求序列化和响应反序列化。
3. Log Mask 在原 Converter 正常读写时取得同一个 Java 对象，生成仅用于日志的 Body。
4. HTTP 调用结束后，通过 `log.mask.http` 打印一条完整 JSON 日志。

Log Mask 不改变原有 Converter 的类型判断和真实读写行为，不修改请求工厂，也不会配置连接池、超时、TLS、代理或重试。

# 选择 RestTemplate

## 应用中没有 RestTemplate

容器中没有 RestTemplate Bean 时，Starter 会使用应用的 `RestTemplateBuilder` 创建名为 `logMaskRestTemplate` 的默认 Bean，并自动选择它。

正式项目仍建议由应用声明自己的 RestTemplate，并配置所需的连接池、超时、TLS、代理和重试。

## 方式一：使用注解

```java
@Bean
@ObservedRestTemplate
public RestTemplate partnerRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

## 方式二：配置 Bean 名称

无法添加注解时，可以配置当前 ApplicationContext 中的 Bean 名称：

```yaml
log-mask:
  logging:
    rest-template:
      observed-bean-names:
        - partnerRestTemplate
```

配置的名称不存在或对应 Bean 不是 RestTemplate 时，应用会启动失败。

## 方式三：使用代码选择

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot3.RestTemplateObservationConfigurer;
import org.springframework.context.annotation.Bean;

@Bean
public RestTemplateObservationConfigurer partnerSelection() {
    return (beanName, restTemplate) ->
            "partnerRestTemplate".equals(beanName);
}
```

三种方式可以同时使用。同一个 RestTemplate 被多种方式选中时，只会安装一次，不会重复打印调用日志。

# Micrometer Observation 与链路追踪

Log Mask 可以与 Spring Boot 3 配置的 Micrometer Observation 同时使用。它不会替换或关闭应用的 `ObservationRegistry`、指标、链路追踪、Interceptor 或其他 `RestTemplateBuilder` 配置，同一个请求可以同时进入应用的 Observation 和 Log Mask 调用日志。

Log Mask 不创建 Span，也不要求应用启用 Actuator 或 Tracing。开启 Trace ID 读取后，它只按配置从 MDC 读取已有值，不会写入 MDC。

# 日志与脱敏配置

下面这些内容在 Spring Boot 2 和 Spring Boot 3 中完全相同，统一放在 [RestTemplate 通用使用指南](../log-mask-resttemplate/README.zh-CN.md)：

| 内容 | 文档 |
| --- | --- |
| 完整日志 key 与状态 | [日志结构](../log-mask-resttemplate/README.zh-CN.md#日志结构) |
| Logger 与 Appender | [日志输出](../log-mask-resttemplate/README.zh-CN.md#日志输出) |
| Path 参数、开关和规则 | [Path](../log-mask-resttemplate/README.zh-CN.md#path) |
| Query 参数、开关和规则 | [Query](../log-mask-resttemplate/README.zh-CN.md#query) |
| Request Header 参数、开关和规则 | [Request Header](../log-mask-resttemplate/README.zh-CN.md#request-header) |
| Request Body 参数、开关和规则 | [Request Body](../log-mask-resttemplate/README.zh-CN.md#request-body) |
| Response Header 参数、开关和规则 | [Response Header](../log-mask-resttemplate/README.zh-CN.md#response-header) |
| Response Body 参数、开关和规则 | [Response Body](../log-mask-resttemplate/README.zh-CN.md#response-body) |
| Trace ID 与自定义脱敏策略 | [Trace ID](../log-mask-resttemplate/README.zh-CN.md#trace-id) |
| 调用日志排查 | [常见问题](../log-mask-resttemplate/README.zh-CN.md#常见问题) |

# Spring Boot 版本说明

Boot 3 Starter 只能用于 Spring Boot 3。Spring Boot 3.0 和 3.1 不在支持范围内；Spring Boot 2 应用请使用 `log-mask-resttemplate-spring-boot2-starter`。使用错误代际的 Starter，或者同时引入 Boot 2 和 Boot 3 Starter 时，应用会在启动阶段给出明确错误。

支持普通 JVM 运行方式，包括 `java -jar`。当前版本不承诺兼容 Spring AOT 或 GraalVM Native Image。

# 相关文档

- [Log Mask 项目主页](../README.zh-CN.md)
- [RestTemplate 通用使用指南](../log-mask-resttemplate/README.zh-CN.md)
- [Core 使用指南](../log-mask-core/README.zh-CN.md)
- [Spring Boot 3 可运行示例](../log-mask-samples-spring-boot3/README.md)
- [Spring Boot 2 Starter 使用指南](../log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)
- [安全策略](../SECURITY.md)
