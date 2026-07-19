# log-mask Spring Boot 3 samples

这是一个使用 Java 17 字节码的 Spring Boot 3 可执行示例，可在 JDK 17 或 21 上运行。
它启动本地 HTTP 服务，并用 `RestTemplate` 调用自身；每次受观测调用都会在
`log.mask.http` 输出一条紧凑 JSON 事件。

## 启动

在仓库根目录构建并运行可执行 jar：

```bash
mvn -pl log-mask-samples-spring-boot3 -am package
java -jar log-mask-samples-spring-boot3/target/log-mask-samples-spring-boot3-0.1.0.jar
```

默认使用 Spring Boot 3.5.16。默认 profile 会让自动配置创建唯一的
`logMaskRestTemplate`，随后调用以下本地端点：

| 方法 | 端点 | 场景 |
| --- | --- | --- |
| `POST` | `/samples/customers/customer-42?token=actual-token&visible=ok` | 类型化 JSON、`@Mask`、`@LogExclude`，以及 path、query、request header、response header 治理。 |
| `POST` | `/samples/strings` | 内容看似 JSON 的 `String`，日志仍为 JSON string。 |
| `POST` | `/samples/bytes` | `byte[]`，日志为 Base64 JSON string。 |
| `GET` | `/samples/no-body` | `204 No Content` 的无 request/response body 调用。 |

`application.properties` 已选择 `COMPACT` name/value 形态，并配置客户请求中的 URI、
query 和 header `REDACT` 规则。日志治理不会改变端点收到的请求、响应 DTO 或 response
header。

## 显式选择与失败场景

`selection-demo` 使用独立 Spring 上下文，演示注解、Bean 名和编程式选择。`shared`
同时命中三种方式，但只会安装一条观测链并记录一次：

```bash
java -jar log-mask-samples-spring-boot3/target/log-mask-samples-spring-boot3-0.1.0.jar --spring.profiles.active=selection-demo
```

这个 profile 会依次调用 `/samples/selection/annotated`、
`/samples/selection/by-name`、`/samples/selection/programmatic` 和
`/samples/selection/shared`。

`request-only-demo` 使用确定性失败的本地传输工厂调用 `/samples/failure`。启动后访问
`/samples/request-only` 会触发该调用。样例保留原始 `ResourceAccessException`，日志
只包含 request，`response` 为 `null`：

```bash
java -jar log-mask-samples-spring-boot3/target/log-mask-samples-spring-boot3-0.1.0.jar --spring.profiles.active=request-only-demo
# 另一个终端：curl -i http://127.0.0.1:8080/samples/request-only
```

只启动端点、手动发起请求时，可以关闭自动演示调用：

```bash
java -jar log-mask-samples-spring-boot3/target/log-mask-samples-spring-boot3-0.1.0.jar --log-mask.samples.demo.enabled=false
```

## 验证

集成测试启动嵌入式 Tomcat，并通过 `log.mask.http` 的专用 `INFO` 捕获器断言每个样例
调用只产生一条合法、单行、紧凑 JSON。测试还检查治理不会修改真实请求和响应，覆盖
类型化与非类型化 body、区域关闭、治理关闭、未选择实例和 request-only 失败。

使用默认 Spring Boot 版本运行：

```bash
mvn -pl log-mask-samples-spring-boot3 -am test
```

指定受支持的 Spring Boot 版本运行：

```bash
mvn -pl log-mask-samples-spring-boot3 -am "-Dspring-boot3.version=3.2.12" test
```

兼容矩阵覆盖 Spring Boot 3.2.12、3.3.13、3.4.13、3.5.16 与 JDK 17、21 的全部八个
组合。将命令中的 `spring-boot3.version` 替换为对应版本即可在本地复现。支持普通
JVM 运行，包括 `java -jar`；当前不承诺 Spring AOT 或 GraalVM Native Image。
