# Log Mask RestTemplate 通用使用指南

[English](README.md) | **简体中文**

本指南说明 Spring Boot 2 和 Spring Boot 3 RestTemplate Starter 共用的日志结构、内容开关和脱敏配置。依赖坐标、Java 版本、包名与 RestTemplate 选择方式见对应的 Starter 指南：

- [Spring Boot 2 Starter 使用指南](../log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)
- [Spring Boot 3 Starter 使用指南](../log-mask-resttemplate-spring-boot3-starter/README.zh-CN.md)

## 日志输出

调用日志使用 `log.mask.http` Logger。开启 INFO 后，每次 HTTP 调用最多打印一条单行 JSON 日志，请求和响应位于同一条日志中：

```yaml
logging:
  level:
    log.mask.http: INFO
```

这个 Logger 没有开启 INFO 时，Log Mask 不采集也不输出调用内容。

### 全局开关

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.enabled` | `true` | RestTemplate 调用日志总开关；关闭后不安装日志能力，也不采集或输出调用日志 |
| `logging.level.log.mask.http` | 由应用日志配置决定 | 设置为 `INFO` 时开启调用日志，设置为 `OFF` 时停止采集和输出 |
| `log-mask.governance.enabled` | `true` | 是否执行 Path、Query、Header 和 Body 的脱敏与排除规则 |

`log-mask.governance.enabled=false` 不会关闭调用日志。它会跳过所有脱敏和排除规则，让已采集的值按原文进入日志。

没有匹配规则的 Path、Query 和 Header 值，以及没有添加 `@Mask` 或 `@LogExclude` 的 Body 字段，也会按原值写入日志。

### Appender

使用 Spring Boot 默认日志配置时，日志会输出到控制台。需要写入独立 JSON Lines 文件时，可以为 `log.mask.http` 配置单独的 Appender。

Logback 示例：

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

Log4j2 示例：

```xml
<File name="LogMaskHttp" fileName="logs/log-mask-http.jsonl">
    <PatternLayout pattern="%m%n" charset="UTF-8" />
</File>

<Logger name="log.mask.http" level="info" additivity="false">
    <AppenderRef ref="LogMaskHttp" />
</Logger>
```

Log Mask 不会创建 Appender，也不会修改 Root Logger。

## 日志结构

默认使用 `STANDARD` 结构时，每个 key 的含义如下：

```text
{}                                  # HTTP 调用日志根对象
├── event                         # 固定为 http_exchange
├── schemaVersion                 # 日志结构版本，当前为 1
├── timestamp                     # 调用结束并准备输出日志时的 UTC 时间
├── exchangeId                    # Log Mask 为本次 HTTP 调用生成的唯一标识
├── traceId                       # 从 MDC 读取的链路标识，没有时为 null
├── durationMs                    # 后续 Interceptor 链的执行耗时，单位为毫秒
├── governanceEnabled             # 本次调用是否启用脱敏和排除规则
├── request                       # 请求日志
│   ├── method                    # HTTP 请求方法
│   ├── uriState                  # URI 日志内容的处理结果
│   ├── uri                       # 处理后的请求地址
│   │   ├── full                  # 处理后的完整 URL，始终输出
│   │   ├── scheme                # 小写的 URI scheme
│   │   ├── host                  # 小写的目标主机名
│   │   ├── port                  # 实际生效的目标端口，无法确定时为 null
│   │   ├── path                  # 保留百分号编码的处理后 Path
│   │   └── query                 # 处理后的 Query 参数数组
│   │       └── entry             # 每个 Query 参数
│   │           ├── name          # Query 参数名
│   │           └── values        # 该参数对应的全部值
│   ├── headersState              # Request Header 日志内容的处理结果
│   ├── headers                   # 处理后的 Request Header 数组
│   │   └── entry                 # 每个 Request Header
│   │       ├── name              # 小写的 Header 名称
│   │       └── values            # 该 Header 对应的全部值
│   ├── bodyState                 # Request Body 日志内容的处理结果
│   └── body                      # 处理后的 Request Body
└── response                      # 响应日志，未取得响应时为 null
    ├── status                    # HTTP 响应状态码
    ├── headersState              # Response Header 日志内容的处理结果
    ├── headers                   # 处理后的 Response Header 数组
    │   └── entry                 # 每个 Response Header
    │       ├── name              # 小写的 Header 名称
    │       └── values            # 该 Header 对应的全部值
    ├── bodyState                 # Response Body 日志内容的处理结果
    └── body                      # 处理后的 Response Body
```

请求已经发出但没有取得响应时，`response` 为 `null`，原始业务异常继续抛给调用方。

`uriState`、`headersState` 和 `bodyState` 的取值如下：

| 状态 | 适用内容 | 说明 |
| --- | --- | --- |
| `SUCCESS` | URI、Header、Body | 正常生成日志内容 |
| `FALLBACK_APPLIED` | URI、Header | 某条规则失败，已对对应值使用固定替代内容 |
| `LIMIT_EXCEEDED` | Body | Body 超出最大日志大小 |
| `PROCESSING_FAILED` | URI | URI 无法完整处理，仍会输出保底内容 |
| `PROCESSING_FAILED` | Body | Body 日志内容无法生成，此时 Body 为空字符串 |
| `DISABLED` | Header、Body | 对应内容已通过配置关闭 |

`durationMs` 从 Log Mask Interceptor 进入后续 Interceptor 链开始计算，到取得响应或发生异常时结束。它包含鉴权、重试、退避和网络调用，不包含请求与响应 Converter 的处理时间。

## Path

HTTP Method 和完整 URL 始终记录，不能单独关闭。`uri.details-enabled` 只控制 `scheme`、`host`、`port`、`path` 和 `query` 这些独立字段，关闭后 `uri.full` 仍然保留，Path 与 Query 规则也会继续处理完整 URL。

### 开关与参数

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.uri.details-enabled` | `true` | 是否在完整 URL 之外输出 `scheme`、`host`、`port`、`path` 和 `query` |
| `log-mask.governance.http.path.rules` | `[]` | Path 脱敏规则；空列表表示不处理任何 Path 参数 |

每条 Path 规则包含：

| 参数 | 是否必填 | 说明 |
| --- | --- | --- |
| `pattern` | 是 | 绝对 Path 模板，例如 `/customers/{id}` |
| `host` | 否 | 只处理指定主机，不区分大小写 |
| `method` | 否 | 只处理指定 HTTP Method，不区分大小写 |
| `variables[].name` | 是 | 对应模板中的 `{name}` |
| `variables[].type` | 与 `type-code` 二选一 | 内置脱敏类型 |
| `variables[].type-code` | 与 `type` 二选一 | 自定义脱敏策略 code |

内置类型支持 `REDACT`、`PHONE`、`EMAIL`、`ID_CARD`、`BANK_CARD` 和 `FULL`，Path 参数不支持 `EXCLUDE`。模板中的每个 `{name}` 都必须配置对应变量；`*` 只匹配一个保持原样的 Path 段，不支持 `**`。

### FULL 示例

```yaml
log-mask:
  governance:
    http:
      path:
        rules:
          - pattern: /customers/{id}
            variables:
              - name: id
                type: FULL
```

真实请求：

```text
https://api.example.com/customers/ABC
```

日志中的 URI：

```json
{
  "full": "https://api.example.com/customers/%2A%2A%2A",
  "scheme": "https",
  "host": "api.example.com",
  "port": 443,
  "path": "/customers/%2A%2A%2A",
  "query": []
}
```

`FULL` 把 `ABC` 替换为等长的 `***`，星号进入 URL 后编码为 `%2A%2A%2A`。真实请求地址不会被修改。

## Query

Query 没有独立关闭开关。完整 URL 始终保留 Query；`uri.details-enabled=false` 只会移除单独的 `request.uri.query` 字段。Query 规则仍会继续处理 `request.uri.full`。

### 开关与参数

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.uri.details-enabled` | `true` | 是否额外输出结构化 `request.uri.query` |
| `log-mask.logging.rest-template.name-value-shape` | `STANDARD` | Query 与 Header 的 JSON 结构；可改为 `COMPACT` |
| `log-mask.governance.http.query.rules` | `[]` | Query 脱敏与排除规则；空列表表示所有参数按原值记录 |

每条 Query 规则包含：

| 参数 | 是否必填 | 说明 |
| --- | --- | --- |
| `name` | 是 | Query 参数名，UTF-8 解码后按大小写精确匹配 |
| `host` | 否 | 只处理指定主机，不区分大小写 |
| `type` | 与 `type-code` 二选一 | 内置脱敏或排除类型 |
| `type-code` | 与 `type` 二选一 | 自定义脱敏策略 code |

内置类型支持 `REDACT`、`EXCLUDE`、`PHONE`、`EMAIL`、`ID_CARD`、`BANK_CARD` 和 `FULL`。Query 规则不能按 HTTP Method 或 Path 限定范围。

### FULL 示例

```yaml
log-mask:
  governance:
    http:
      query:
        rules:
          - name: token
            type: FULL
```

真实请求：

```text
https://api.example.com/search?token=ABC&visible=ok
```

日志中的 URI：

```json
{
  "full": "https://api.example.com/search?token=%2A%2A%2A&visible=ok",
  "scheme": "https",
  "host": "api.example.com",
  "port": 443,
  "path": "/search",
  "query": [
    {"name":"token","values":["***"]},
    {"name":"visible","values":["ok"]}
  ]
}
```

`visible` 没有匹配规则，因此保持原值。真实请求参数不会被修改。

### Query 结构

`STANDARD` 输出数组：

```json
[
  {"name":"token","values":["***"]}
]
```

使用 `COMPACT` 时输出对象：

```yaml
log-mask:
  logging:
    rest-template:
      name-value-shape: COMPACT
```

```json
{
  "token": ["***"]
}
```

这个配置也会改变 Request Header 和 Response Header 的结构。

## Request Header

Request Header 默认记录，可以单独关闭。关闭后 `request.headersState` 为 `DISABLED`，`request.headers` 为 `null`，其他请求与响应内容继续记录。

### 开关与参数

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.request.headers-enabled` | `true` | 是否记录 Request Header |
| `log-mask.logging.rest-template.name-value-shape` | `STANDARD` | Header 的 JSON 结构；可改为 `COMPACT` |
| `log-mask.governance.http.headers.request.rules` | `[]` | Request Header 脱敏与排除规则；空列表表示所有 Header 按原值记录 |

每条 Request Header 规则包含：

| 参数 | 是否必填 | 说明 |
| --- | --- | --- |
| `name` | 是 | Header 名称，匹配不区分大小写 |
| `host` | 否 | 只处理指定主机，不区分大小写 |
| `type` | 与 `type-code` 二选一 | 内置脱敏或排除类型 |
| `type-code` | 与 `type` 二选一 | 自定义脱敏策略 code |

内置类型支持 `REDACT`、`EXCLUDE`、`PHONE`、`EMAIL`、`ID_CARD`、`BANK_CARD` 和 `FULL`。多值 Header 会逐个处理，日志中的 Header 名称使用小写。

Request Header 在 Log Mask 进入后续 Interceptor 链前采集。鉴权等后续 Interceptor 新增或修改的 Header 不会出现在这条调用日志中。

### FULL 示例

```yaml
log-mask:
  governance:
    http:
      headers:
        request:
          rules:
            - name: X-Api-Key
              type: FULL
```

真实 Request Header：

```text
X-Api-Key: ABC
```

日志中的 `request.headers`：

```json
[
  {"name":"x-api-key","values":["***"]}
]
```

真实 Request Header 不会被修改。

## Request Body

Request Body 默认记录，可以单独关闭。关闭后 `request.bodyState` 为 `DISABLED`，`request.body` 为空字符串，其他请求与响应内容继续记录。

### 开关与参数

| 配置或规则 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.request.body-enabled` | `true` | 是否记录 Request Body |
| `log-mask.logging.rest-template.max-body-size` | `64KB` | 单个 Request Body 的最大日志大小，与 Response Body 分别计算 |
| `@Mask` | 无 | 对明确 Java 类型中的字段执行内置或自定义脱敏策略 |
| `@LogExclude` | 无 | 从日志 Body 中删除字段，真实请求仍保留该字段 |

`max-body-size` 允许设置为 1 至 2,147,483,647 字节。Body 超限时，`request.bodyState` 为 `LIMIT_EXCEEDED`，`request.body` 为空字符串，不会记录截断内容。

### FULL 示例

```java
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;

public final class CredentialRequest {

    @Mask(type = MaskType.FULL)
    public String customerCode;

    @LogExclude
    public String accessToken;
}
```

真实 Request Body：

```json
{
  "customerCode": "ABC",
  "accessToken": "TOKEN-EXAMPLE-ABCD"
}
```

日志中的 `request.body`：

```json
{
  "customerCode": "***"
}
```

真实请求和原业务对象不会被修改。

### Body 类型

| Request Body 类型 | 日志处理方式 |
| --- | --- |
| 明确 Java 类型的业务对象 | 根据 `@Mask`、`@LogExclude` 和自定义策略生成日志 JSON，嵌套对象、集合、数组和 Map 中的业务对象继续执行字段规则 |
| `String` | 作为普通 JSON 字符串记录，不根据字符串内容再次解析 JSON |
| `byte[]` | 使用 Jackson Base64 编码为 JSON 字符串 |
| 其他 Converter 写出的内容 | 按声明字符集解码；没有声明字符集时按严格 UTF-8 解码，无法解码时使用 Base64，不执行字段级规则 |
| 没有 Request Body | 记录为 JSON `null` |

## Response Header

Response Header 默认记录，可以单独关闭。关闭后 `response.headersState` 为 `DISABLED`，`response.headers` 为 `null`，其他请求与响应内容继续记录。

### 开关与参数

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.response.headers-enabled` | `true` | 是否记录 Response Header |
| `log-mask.logging.rest-template.name-value-shape` | `STANDARD` | Header 的 JSON 结构；可改为 `COMPACT` |
| `log-mask.governance.http.headers.response.rules` | `[]` | Response Header 脱敏与排除规则；空列表表示所有 Header 按原值记录 |

每条 Response Header 规则的 `name`、`host`、`type` 和 `type-code` 与 Request Header 相同，但请求与响应使用两套独立规则。

### FULL 示例

```yaml
log-mask:
  governance:
    http:
      headers:
        response:
          rules:
            - name: X-Response-Key
              type: FULL
```

真实 Response Header：

```text
X-Response-Key: ABC
```

日志中的 `response.headers`：

```json
[
  {"name":"x-response-key","values":["***"]}
]
```

真实 Response Header 不会被修改。

## Response Body

Response Body 默认记录，可以单独关闭。关闭后 `response.bodyState` 为 `DISABLED`，`response.body` 为空字符串，其他请求与响应内容继续记录。

### 开关与参数

| 配置或规则 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.response.body-enabled` | `true` | 是否记录 Response Body |
| `log-mask.logging.rest-template.max-body-size` | `64KB` | 单个 Response Body 的最大日志大小，与 Request Body 分别计算 |
| `@Mask` | 无 | 对明确 Java 类型中的字段执行内置或自定义脱敏策略 |
| `@LogExclude` | 无 | 从日志 Body 中删除字段，业务代码得到的响应对象仍保留该字段 |

Body 超限时，`response.bodyState` 为 `LIMIT_EXCEEDED`，`response.body` 为空字符串，不会记录截断内容。

### FULL 示例

```java
import io.github.summerwenlabs.log.mask.governance.LogExclude;
import io.github.summerwenlabs.log.mask.governance.Mask;
import io.github.summerwenlabs.log.mask.governance.MaskType;

public final class CredentialResponse {

    @Mask(type = MaskType.FULL)
    public String customerCode;

    @LogExclude
    public String accessToken;
}
```

真实 Response Body：

```json
{
  "customerCode": "ABC",
  "accessToken": "TOKEN-EXAMPLE-ABCD"
}
```

日志中的 `response.body`：

```json
{
  "customerCode": "***"
}
```

业务代码得到的响应对象不会被修改。

### Body 类型

| Response Body 类型 | 日志处理方式 |
| --- | --- |
| 明确 Java 类型的成功响应 | 根据实际反序列化得到的业务对象生成日志 JSON，并执行字段规则 |
| `String` | 作为普通 JSON 字符串记录，不根据字符串内容再次解析 JSON |
| `byte[]` | 使用 Jackson Base64 编码为 JSON 字符串 |
| 错误响应或其他未类型化响应 | 只记录业务链路实际读取的字节，可以按字符集解码时记录为 JSON 字符串，无法解码时使用 Base64 |
| 没有 Response Body | 记录为 JSON `null` |

Log Mask 不会为了日志预读或补读错误响应。业务链路没有读取的部分不会写入日志；可能存在 Body 但没有读取任何字节时，日志中的 Body 为空字符串。

## Trace ID

Log Mask 默认按顺序从 MDC 的 `traceId` 和 `trace_id` 中读取第一个非空值：

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.logging.rest-template.trace-id.enabled` | `true` | 是否从 MDC 读取 Trace ID |
| `log-mask.logging.rest-template.trace-id.mdc-keys` | `[traceId, trace_id]` | 按顺序查找，第一个非空值写入日志 |

```yaml
log-mask:
  logging:
    rest-template:
      trace-id:
        enabled: true
        mdc-keys:
          - traceId
          - trace_id
```

Log Mask 只读取现有 Trace ID，不会创建或修改 MDC。没有可用值时，日志中的 `traceId` 为 `null`。

## 自定义脱敏策略

应用中的 `MaskTypeDefinition` Bean 会在 Starter 初始化时自动注册。Path、Query 和 Header 规则通过 `type-code` 引用，Body 字段通过 `@Mask` 引用。

自定义策略的定义和注册方式见 [Core 使用指南](../log-mask-core/README.zh-CN.md#自定义脱敏策略)。

## 常见问题

### 为什么没有调用日志

依次检查：

1. 当前 RestTemplate 是否通过注解、Bean 名称或代码选择。
2. `log-mask.logging.rest-template.enabled` 是否为 `true`。
3. `log.mask.http` Logger 是否开启 INFO。
4. 启动日志中的 `observedInstanceCountAtStartup` 是否大于 0。
5. 启动日志中的 `exchangeEventsEnabledAtStartup` 是否为 `true`。

### 是否会自动处理所有 RestTemplate

不会。只有通过注解、Bean 名称或代码选择的 RestTemplate 会安装调用日志能力。

### 为什么 String Body 中的 JSON 字段没有脱敏

String Body 被当作普通字符串记录，不会重新解析为 JSON。需要字段级脱敏时，请使用带有 `@Mask` 或 `@LogExclude` 的明确 Java 类型。

### 为什么 Body 是空字符串

查看对应的 `bodyState`：

- `DISABLED`：Body 日志已关闭
- `LIMIT_EXCEEDED`：Body 超出最大日志大小
- `PROCESSING_FAILED`：Body 日志生成失败

错误响应可能存在 Body，但业务链路没有读取任何字节时，`bodyState` 仍可能为 `SUCCESS`，Body 为空字符串。

## 相关文档

- [Log Mask 项目主页](../README.zh-CN.md)
- [Core 使用指南](../log-mask-core/README.zh-CN.md)
- [Spring Boot 2 Starter 使用指南](../log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)
- [Spring Boot 3 Starter 使用指南](../log-mask-resttemplate-spring-boot3-starter/README.zh-CN.md)
- [安全策略](../SECURITY.md)
