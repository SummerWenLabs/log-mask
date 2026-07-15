# log-mask

`log-mask` 为 Spring Boot 2 的 `RestTemplate` 提供一条紧凑、结构化的 HTTP 交换日志，并只对显式声明的对象、URI、query 和 header 规则执行日志数据治理。它从不修改真实请求、响应、业务对象、MDC 或业务异常。

支持 Java 8、Spring Boot 2.6.15 与 2.7.18。Java 8 是编译和字节码目标；Spring Boot 3、WebClient、Feign、其他 HTTP 客户端和通用日志参数拦截不在 v1 范围内。

## 快速开始

引入 starter：

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

当容器没有任何 `RestTemplate` Bean 时，starter 会通过 `RestTemplateBuilder` 创建一个默认受观测实例。调用该实例后，开启 `log.mask.http` 的 INFO 级别即可看到一条单行 JSON 事件：

```java
ResponseEntity<String> response = restTemplate.getForEntity(
        "https://api.example.test/health", String.class);
```

```json
{"event":"http_exchange","schemaVersion":1,"timestamp":"...","exchangeId":"...","traceId":null,"durationMs":3,"governanceEnabled":true,"request":{"method":"GET",...},"response":{"status":200,...}}
```

可直接运行项目内 sample：

```bash
mvn -pl log-mask-samples -am package
java -jar log-mask-samples/target/log-mask-samples-0.1.0-SNAPSHOT.jar
```

Sample 的场景端点与验证步骤见 [log-mask-samples/README.md](log-mask-samples/README.md)。

## 选择受观测实例

已有 `RestTemplate` 永远保持原状，除非明确选择。三种选择来源按对象身份取并集，因此同一实例被多种方式选中时仍只安装一次观测链并只记录一条事件。

```java
@Bean
@ObservedRestTemplate
RestTemplate partnerTemplate(RestTemplateBuilder builder) {
    return builder.build();
}
```

```yaml
log-mask:
  logging:
    rest-template:
      observed-bean-names: partnerTemplate
```

```java
@Bean
RestTemplateObservationConfigurer partnerSelection() {
    return (beanName, restTemplate) -> "partnerTemplate".equals(beanName);
}
```

注解适合源码可控的 Bean；Bean 名适合第三方提供的 Bean；编程式配置适合 Java 配置。logging 开启时，配置的 Bean 不存在或类型不是 `RestTemplate` 会阻止启动。关闭总 logging 时，不安装 interceptor 或 converter 装饰器，也不校验选择配置。

## 配置与状态

`log-mask.logging.rest-template.enabled` 默认 `true`，优先级高于一切治理配置。`log.mask.http` 的 INFO 未启用时，运行时在请求快照之前旁路采集和治理。

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `log-mask.governance.enabled` | `true` | 统一控制所有显式治理规则；关闭时原样记录，事件仍输出。 |
| `log-mask.logging.rest-template.uri.details-enabled` | `true` | 关闭时只保留治理后的 `request.uri.full`。 |
| `log-mask.logging.rest-template.name-value-shape` | `STANDARD` | `STANDARD` 为 entry 数组，`COMPACT` 为 name 到 values 数组的对象。 |
| `log-mask.logging.rest-template.max-body-size` | `64KiB` | request 与 response 各自独立的最终 UTF-8 JSON 预算，必须为正且有界。 |
| `log-mask.logging.rest-template.trace-id.enabled` | `true` | 是否读取宿主 MDC；组件从不写 MDC。 |
| `log-mask.logging.rest-template.trace-id.mdc-keys` | `traceId`, `trace_id` | 按顺序读取第一个非空值。 |
| `log-mask.logging.rest-template.request.headers-enabled` | `true` | request headers 输出开关。 |
| `log-mask.logging.rest-template.response.headers-enabled` | `true` | response headers 输出开关。 |
| `log-mask.logging.rest-template.request.body-enabled` | `true` | request body 输出开关。 |
| `log-mask.logging.rest-template.response.body-enabled` | `true` | response body 输出开关。 |

每个 URI、header 和 body 区域都有最终状态：`SUCCESS`、`FALLBACK_APPLIED`、`LIMIT_EXCEEDED`、`PROCESSING_FAILED` 或 `DISABLED`。未配置规则的原样输出也是 `SUCCESS`；只有关闭该输出区域才是 `DISABLED`。headers 关闭时输出 `null`，body 关闭时输出空 JSON 字符串；真实不存在的 body 才输出 JSON `null`。

## 对象治理

在 field 或 getter 上使用 `@Mask` 与 `@LogExclude`，规则跟随 Jackson 的逻辑属性和最终 JSON 名称：

```java
final class LoginRequest {
    @Mask(type = MaskType.PHONE)
    private final String phone;

    @Mask(type = MaskType.REDACT)
    private final String password;

    @LogExclude
    private final String unneededPayload;
}
```

`REDACT` 固定输出 `"<redacted>"`；`FULL` 用等量 `*` 保留 Unicode code point 长度；内置类型包含 `PHONE`、`EMAIL`、`ID_CARD` 和 `BANK_CARD`。也可使用内联正则或由 `MaskTypeDefinition` Bean 通过精确 `typeCode` 注册的自定义策略。字段规则失败时只安全遮蔽当前属性并继续记录其余结构；整体对象 JSON 生成失败才成为 body 的 `PROCESSING_FAILED`。

## HTTP 规则与安全提示

**默认没有敏感名称表。** 未配置规则的 `Authorization`、`Cookie`、token、凭证、query、header 和对象属性都会原样进入日志。上线前必须为实际敏感数据声明规则，例如：

```yaml
log-mask:
  governance:
    http:
      path:
        rules:
          - pattern: /customers/{customerId}
            variables:
              - name: customerId
                type: REDACT
      query:
        rules:
          - name: token
            type: REDACT
      headers:
        request:
          rules:
            - name: Authorization
              type: REDACT
            - name: Cookie
              type: EXCLUDE
        response:
          rules:
            - name: Set-Cookie
              type: EXCLUDE
```

Path 规则可按 host 与 method 限定，命名变量必须恰好配置一个 `type` 或 `type-code`；`*` 是原样保留的单段通配符，`**` 无效。Query 和 header 规则可按 host 限定，`REDACT` 保留名称，`EXCLUDE` 删除该 name 的全部出现。规则仅改变日志表示：治理后的 URI、headers 和 body 不会写回实际 HTTP 报文。

URI 日志始终省略 userInfo 与 fragment。Query 保留重复参数、顺序以及 `?flag` 与 `?flag=` 的区别；header 仅在日志中小写化且保留 Spring 提供的 values 顺序。`STANDARD` 与 `COMPACT` 只改变 JSON 形态，不改变治理结果。

## Body 与生命周期边界

类型化 JSON body 使用应用 Converter 实际读写的 Java 值与声明 Type；String 始终作为 JSON string，`byte[]` 始终作为 Base64 JSON string。其他 converter 只使用已观察到的 wire bytes，按声明 charset 或严格 UTF-8 解码，无法解码时使用 Base64。

response 不会被预读、补读、重放或全量缓冲。旁路只同步复制业务 Converter 或错误处理器实际消费的字节；超过 `max-body-size` 时停止日志采集、保留业务读取并输出 `LIMIT_EXCEEDED` 与空字符串。响应 DTO 已忽略的 wire 字段不会被恢复。

每次交换最多输出一条终态事件。同步嵌套调用通过 adapter 私有的 LIFO scope 隔离；response close、converter、错误处理器和传输失败只终结自己的 scope。组件的日志/治理错误不会覆盖业务异常，原异常实例继续向调用方传播。组件不管理连接池、超时、TLS、代理或 `ClientHttpRequestFactory` 配置。

## 模块与质量门禁

```text
log-mask-core -> log-mask-http-core -> log-mask-resttemplate-spring-boot2-autoconfigure -> log-mask-resttemplate-spring-boot2-starter
```

`core` 与 `http-core` 保持 Spring-free；autoconfigure 只选择明确的 `RestTemplate`、装饰受支持的 Converter 并安装交换 interceptor；starter 只聚合依赖。JMH 位于仅通过 `benchmarks` profile 引入的 `log-mask-benchmarks`，不会进入默认 reactor、CI 或发布产物。

本地完整验证：

```bash
mvn clean verify -Dspring-boot.version=2.6.15
mvn clean verify -Dspring-boot.version=2.7.18
pwsh ./scripts/verify-quality-gates.ps1
mvn -Pbenchmarks -pl log-mask-benchmarks -am clean package
```

CI 在 Boot 2.6.15/2.7.18 与 JDK 8/11/17 的六个组合中从干净工作区运行上述默认验证，并检查 Java 8 class 文件、模块单向依赖、core/http-core 的 Spring 依赖图，以及 Apache、OkHttp、Netty 等 HTTP transport 和连接池依赖禁令。性能基准不设跨机器绝对阈值，结果只在相同环境内比较。
