# log-mask samples

这是一个 Java 8、Spring Boot 2.6.15/2.7.18 的可执行示例。它启动本地 HTTP 服务，并用 `RestTemplate` 调用自身；每次受观测调用都会在 `log.mask.http` 输出一条紧凑 JSON 事件。

## 启动

在仓库根目录构建并运行可执行 jar：

```bash
mvn -pl log-mask-samples -am package
java -jar log-mask-samples/target/log-mask-samples-0.1.0-SNAPSHOT.jar
```

默认 profile 会让自动配置创建唯一的 `logMaskRestTemplate`，随后调用下列本地端点：

| 方法 | 端点 | 场景 |
| --- | --- | --- |
| `POST` | `/samples/customers/customer-42?token=actual-token&visible=ok` | 类型化 JSON、`@Mask`、`@LogExclude`、path/query/request header/response header 治理。 |
| `POST` | `/samples/strings` | 内容看似 JSON 的 `String`，日志仍为 JSON string。 |
| `POST` | `/samples/bytes` | `byte[]`，日志为 Base64 JSON string。 |
| `GET` | `/samples/no-body` | `204 No Content` 的无 request/response body 调用。 |

`application.properties` 已选择 `COMPACT` name/value 形态，并配置上述客户请求的 URI、query 和 header `REDACT` 规则。日志治理不会改变端点收到的请求、响应 DTO 或 response header。

## 显式选择与失败场景

`selection-demo` 用独立 Spring 上下文避免与默认实例冲突。它演示注解、Bean 名和编程式选择，并让 `shared` 同时命中三种方式，以验证同一实例只装饰和记录一次：

```bash
java -jar log-mask-samples/target/log-mask-samples-0.1.0-SNAPSHOT.jar --spring.profiles.active=selection-demo
```

`request-only-demo` 使用一个确定性失败的本地传输工厂调用 `/samples/failure`。启动后访问 `/samples/request-only` 会触发该调用；样例不捕获或替换 `ResourceAccessException`，日志只包含 request 且 `response` 为 `null`：

```bash
java -jar log-mask-samples/target/log-mask-samples-0.1.0-SNAPSHOT.jar --spring.profiles.active=request-only-demo
# 另一个终端：curl -i http://127.0.0.1:8080/samples/request-only
```

需要只启动端点、手动发起请求时，可关闭自动演示调用：

```bash
java -jar log-mask-samples/target/log-mask-samples-0.1.0-SNAPSHOT.jar --log-mask.samples.demo.enabled=false
```

## 可验证行为

集成测试通过嵌入式 Tomcat 和 `log.mask.http` 的专用 INFO 捕获器，逐个断言每个样例调用只产生一条合法、单行、紧凑 JSON。它还覆盖关闭 request body 区域的 `DISABLED`/空字符串语义、`log-mask.governance.enabled=false` 时的原样记录、未选择实例零日志，以及 request-only 失败的异常身份。

```bash
mvn -pl log-mask-samples -am test
mvn -pl log-mask-samples -am "-Dspring-boot2.version=2.6.15" test
```
