# Log Mask RestTemplate Spring Boot 2 Starter

[English](README.md) · **简体中文**

> 给指定的 `RestTemplate` 加上统一调用日志，并对日志中的敏感数据脱敏。

## 为什么使用

1. `RestTemplate` 没有可直接使用的请求、响应和耗时日志，线上问题缺少排查依据。
2. 各个项目自行打印日志后，格式不统一，脱敏逻辑也需要重复开发。
3. 安全审查不能只靠删除日志解决，否则后续排查仍然困难。

## 模块介绍

这个 Starter 支持 Java 8、Spring Boot 2.6.15 和 2.7.18。给 `RestTemplate` Bean 添加
`@ObservedRestTemplate` 后，HTTP 调用会通过 `log.mask.http` 输出 JSON 日志。

脱敏只处理日志内容，不会修改真实请求、响应、业务对象、MDC、RestTemplate 配置或
业务异常。

Log Mask 只处理明确配置或标记的数据。没有规则的 Path、Query 参数、Header 和 Body
字段会原样写入日志，使用前需要把敏感数据规则配置完整。

## 核心特性

- 使用 `@ObservedRestTemplate` 选择需要记录日志的客户端。
- 监听启用且 `log.mask.http` 开启 `INFO` 时，每个已完成的 HTTP 调用最多写一条 JSON
  日志，请求和响应放在同一条日志中。
- 支持 Path、Query、请求 Header 和响应 Header 脱敏。
- 使用 `@Mask` 和 `@LogExclude` 处理请求 Body 与响应 Body。
- 请求 Header、请求 Body、响应 Header 和响应 Body 可以分别关闭。
- 支持从 MDC 读取 trace ID，不会修改 MDC。

## 快速开始

### Maven

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 1. 选择 RestTemplate

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

### 2. 标记 Body 字段

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

### 3. 开启调用日志

```yaml
logging:
  level:
    log.mask.http: INFO
```

实际发送的请求 Body：

```json
{"phone":"13800138000","accessToken":"actual-token"}
```

日志中的 `request.body`：

```json
{"phone":"138****8000"}
```

真实请求仍使用原手机号和 access token，只有日志内容发生变化。

## 选择 RestTemplate Bean

### 应用中没有 RestTemplate

容器中没有 `RestTemplate` Bean 时，Starter 会使用应用的 `RestTemplateBuilder` 创建
`logMaskRestTemplate`。Log Mask 不会为它配置连接池、超时、TLS、代理或重试。正式
项目建议由应用声明并配置自己的 `RestTemplate`。

### 应用中已有 RestTemplate

已有 Bean 可以使用下面三种方式选择。三种方式可以同时使用，同一个实例不会重复
记录。

1. 在 Bean 上添加注解：

```java
@Bean
@ObservedRestTemplate
public RestTemplate partnerRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

2. 无法添加注解时，在 YAML 中配置 Bean 名称：

```yaml
log-mask:
  logging:
    rest-template:
      observed-bean-names:
        - partnerRestTemplate
```

3. 需要按代码条件选择时，声明 `RestTemplateObservationConfigurer` Bean：

```java
import io.github.summerwenlabs.log.mask.resttemplate.boot2.RestTemplateObservationConfigurer;
import org.springframework.context.annotation.Bean;

@Bean
public RestTemplateObservationConfigurer partnerSelection() {
    return (beanName, restTemplate) ->
            "partnerRestTemplate".equals(beanName);
}
```

配置的 Bean 名称必须能在当前 `ApplicationContext` 中找到，并且对应的 Bean 必须是
`RestTemplate`。名称不存在或类型不正确时，应用会启动失败。

## 日志输出

使用 Spring Boot 默认日志配置时，调用日志会输出到控制台。`log.mask.http` 的日志
内容是完整 JSON。`INFO` 未开启时，不采集也不输出调用内容。

需要单独写入 JSON Lines 文件时，可以为这个 logger 配置独立 appender，并关闭
additivity。Log Mask 不会创建 appender，也不会修改 root logger。

Logback 的 `logback-spring.xml` 节点示例：

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

Log4j2 的 `log4j2-spring.xml` 节点示例：

```xml
<File name="LogMaskHttp" fileName="logs/log-mask-http.jsonl">
  <PatternLayout pattern="%m%n" charset="UTF-8" />
</File>

<Logger name="log.mask.http" level="info" additivity="false">
  <AppenderRef ref="LogMaskHttp" />
</Logger>
```

## Path 脱敏

把 Path 模板和需要脱敏的变量写入配置：

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

真实请求：

```text
https://api.example.com/customers/42
```

日志中的 URI：

```json
{"request":{"uri":{"full":"https://api.example.com/customers/%3Credacted%3E","path":"/customers/%3Credacted%3E"}}}
```

`<redacted>` 在 URI 中会编码为 `%3Credacted%3E`，真实请求仍访问
`/customers/42`。规则还可以使用 `host` 和 `method` 限定范围。Path 变量不支持
`EXCLUDE`。

## Query 参数脱敏

按 Query 参数名配置规则：

```yaml
log-mask:
  governance:
    http:
      query:
        rules:
          - name: token
            type: REDACT
```

真实请求：

```text
https://api.example.com/search?token=actual&visible=ok
```

日志中的 URI：

```json
{"request":{"uri":{"full":"https://api.example.com/search?token=%3Credacted%3E&visible=ok","query":[{"name":"token","values":["<redacted>"]},{"name":"visible","values":["ok"]}]}}}
```

未配置的 `visible` 保留原值，真实请求中的 `token` 仍是 `actual`。Query 规则可以用
`host` 限定范围，也可以使用 `EXCLUDE` 从日志中删除整个参数。

## Request Header 脱敏

请求 Header 使用 `headers.request.rules`：

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

真实请求 Header：

```text
Authorization: Bearer request-secret
```

日志中的 `request.headers`：

```json
[{"name":"authorization","values":["<redacted>"]}]
```

Header 名匹配不区分大小写，日志中的名称使用小写，真实请求 Header 不变。

## Response Header 脱敏

响应 Header 使用独立的 `headers.response.rules`：

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

真实响应 Header：

```text
X-Response-Token: response-secret
```

日志中的 `response.headers`：

```json
[{"name":"x-response-token","values":["<redacted>"]}]
```

请求和响应 Header 使用两套规则，互不影响。Query 和 Header 规则都支持
`REDACT`、`EXCLUDE`、`PHONE`、`EMAIL`、`ID_CARD`、`BANK_CARD` 和 `FULL`。

## Request Body 脱敏

在请求对象的字段或 getter 上使用 `@Mask`：

```java
@Mask(type = MaskType.PHONE)
public String getPhone() {
    return phone;
}
```

实际发送的 Body：

```json
{"phone":"13800138000"}
```

日志中的 `request.body`：

```json
{"phone":"138****8000"}
```

需要完全删除的字段使用 `@LogExclude`。实际发送内容和原请求对象不会改变。

## Response Body 脱敏

响应对象使用相同的字段注解：

```java
@Mask(type = MaskType.PHONE)
public String getPhone() {
    return phone;
}
```

实际响应 Body：

```json
{"phone":"13900139000"}
```

日志中的 `response.body`：

```json
{"phone":"139****9000"}
```

业务代码拿到的响应对象仍保留原值。Body 按字段脱敏需要使用明确的 Java 对象类型；
`String` Body 不会按 JSON 内容再次解析。

## 关闭功能

下面几个开关作用不同：

| 需要关闭的内容 | 配置 | 效果 |
| --- | --- | --- |
| 全部 RestTemplate 调用日志 | `log-mask.logging.rest-template.enabled=false` | 不监听、不采集、不输出调用日志 |
| 临时停止日志输出 | `logging.level.log.mask.http=OFF` | 不采集、不输出调用内容 |
| 全部脱敏和排除规则 | `log-mask.governance.enabled=false` | 继续写调用日志，但 Path、Query、Header 和 Body 按原值记录 |
| 请求 Header | `log-mask.logging.rest-template.request.headers-enabled=false` | `request.headers` 为 `null`，其他内容照常记录 |
| 请求 Body | `log-mask.logging.rest-template.request.body-enabled=false` | `request.body` 为空字符串，其他内容照常记录 |
| 响应 Header | `log-mask.logging.rest-template.response.headers-enabled=false` | `response.headers` 为 `null`，其他内容照常记录 |
| 响应 Body | `log-mask.logging.rest-template.response.body-enabled=false` | `response.body` 为空字符串，其他内容照常记录 |

例如，只关闭响应 Body：

```yaml
log-mask:
  logging:
    rest-template:
      response:
        body-enabled: false
```

`governance.enabled=false` 不是关闭日志。它会让敏感值按原文进入日志，只应在明确需要
原始内容时使用。

## 配置参数

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.governance.enabled` | `true` | 是否执行所有脱敏和排除规则 |
| `log-mask.governance.http.path.rules` | `[]` | Path 脱敏规则 |
| `log-mask.governance.http.query.rules` | `[]` | Query 参数脱敏规则 |
| `log-mask.governance.http.headers.request.rules` | `[]` | 请求 Header 脱敏规则 |
| `log-mask.governance.http.headers.response.rules` | `[]` | 响应 Header 脱敏规则 |
| `log-mask.logging.rest-template.enabled` | `true` | RestTemplate 调用日志总开关 |
| `log-mask.logging.rest-template.observed-bean-names` | `[]` | 通过 Bean 名称选择当前上下文中的 RestTemplate |
| `log-mask.logging.rest-template.uri.details-enabled` | `true` | 是否额外记录 scheme、host、port、path 和 query；关闭后仍保留完整 URI |
| `log-mask.logging.rest-template.name-value-shape` | `STANDARD` | Query 和 Header 使用数组结构；`COMPACT` 使用名称到值数组的对象结构 |
| `log-mask.logging.rest-template.max-body-size` | `64KB` | 每个请求 Body 和响应 Body 在日志中的大小限制，范围为 1 到 2,147,483,647 字节；超限的一侧 Body 为空字符串，其他内容照常记录 |
| `log-mask.logging.rest-template.request.headers-enabled` | `true` | 是否记录请求 Header |
| `log-mask.logging.rest-template.request.body-enabled` | `true` | 是否记录请求 Body |
| `log-mask.logging.rest-template.response.headers-enabled` | `true` | 是否记录响应 Header |
| `log-mask.logging.rest-template.response.body-enabled` | `true` | 是否记录响应 Body |
| `log-mask.logging.rest-template.trace-id.enabled` | `true` | 是否从 MDC 读取 trace ID |
| `log-mask.logging.rest-template.trace-id.mdc-keys` | `[traceId, trace_id]` | 按顺序读取，第一个非空值写入日志 |

`STANDARD` 的日志结果：

```json
[{"name":"token","values":["<redacted>"]}]
```

`COMPACT` 的日志结果：

```json
{"token":["<redacted>"]}
```

HTTP 规则中的 `type-code` 可以引用应用提供的 `MaskTypeDefinition` Bean。自定义策略的
写法见 [Core 指南](../log-mask-core/README.zh-CN.md#自定义策略)。

## 相关文档

- [Core 指南](../log-mask-core/README.zh-CN.md)
- [可运行示例](../log-mask-samples/README.md)
- [安全策略](../SECURITY.md)
