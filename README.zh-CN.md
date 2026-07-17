# Log Mask

[English](README.md) · **简体中文**

> 一个只处理日志内容、不修改业务数据的 Java 日志脱敏组件。

## 为什么创建它

Log Mask 源于实际项目中遇到的几个问题：

1. 公司内部的日志安全要求越来越严格，敏感信息不能再直接写入日志。
2. `RestTemplate` 本身没有可直接使用的调用日志，线上问题缺少请求、响应和耗时等
   排查线索。
3. 各个项目自行补充日志后，格式不统一，脱敏逻辑也需要重复开发和维护。
4. 安全审查时，如果直接删除调用日志，虽然暂时避开了敏感信息问题，后续排查却失去
   了必要依据。
5. 没有找到一套既简单、又能快速接入，同时处理日志记录和脱敏的现成方案。

## 项目介绍

Log Mask 是一个模块化的 Java 日志脱敏项目，既可以单独处理 Java 对象，也可以监听
Spring Boot 2 应用中的 `RestTemplate`。项目提供两个面向应用的 Maven 制品：

| 使用场景 | Maven 制品 | 运行环境 |
| --- | --- | --- |
| 把 Java 对象转成脱敏后的日志 JSON | `log-mask-core` | Java 8 |
| 监听 Spring Boot 2 `RestTemplate` 并记录调用日志 | `log-mask-resttemplate-spring-boot2-starter` | Java 8，Spring Boot 2.6.15 或 2.7.18 |

Core 不依赖 Spring。RestTemplate Starter 已包含 Core，应用不需要重复引入。

Log Mask 只处理明确标记或配置的数据。没有规则的字段、路径参数、查询参数和 HTTP
头会原样写入日志，使用前需要把敏感数据的规则配置完整。

## 核心特性

- 使用 `@Mask` 脱敏字段，使用 `@LogExclude` 从日志中排除字段。
- 自动处理嵌套对象、集合、数组和 Map 中的字段规则。
- 支持内置脱敏类型、正则替换和应用自定义策略。
- Core 可独立使用，`LogMasker` 构建后可以在线程间复用。
- 通过 `@ObservedRestTemplate` 选择要监听的 `RestTemplate`，记录请求、响应和耗时。
- 只修改日志内容，不修改业务对象、实际请求、响应、MDC、RestTemplate 配置或业务
  异常。

## 快速开始

### 处理 Java 对象

Maven：

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
final class LoginRequest {
    @Mask(type = MaskType.PHONE)
    public final String phone;

    LoginRequest(String phone) {
        this.phone = phone;
    }
}

LogMasker logMasker = LogMasker.builder(new ObjectMapper()).build();
String logJson = logMasker.mask(new LoginRequest("13800138000"));
// {"phone":"138****8000"}
```

字段规则、自定义策略、大小限制和异常说明见
[Core 指南](log-mask-core/README.zh-CN.md)。

### 监听 RestTemplate

Maven：

```xml
<dependency>
  <groupId>io.github.summerwenlabs</groupId>
  <artifactId>log-mask-resttemplate-spring-boot2-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
@Bean
@ObservedRestTemplate
public RestTemplate partnerRestTemplate(RestTemplateBuilder builder) {
    return builder.build();
}

final class PartnerRequest {
    @Mask(type = MaskType.PHONE)
    public final String phone;

    PartnerRequest(String phone) {
        this.phone = phone;
    }
}
```

`@ObservedRestTemplate` 监听指定的客户端，`@Mask` 只处理请求或响应对象在日志中的
内容。Bean 选择、完整配置、HTTP 数据规则和启动日志说明见
[RestTemplate Starter 指南](log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)。

## 文档

- [Core 指南](log-mask-core/README.zh-CN.md)
- [RestTemplate Starter 指南](log-mask-resttemplate-spring-boot2-starter/README.zh-CN.md)
- [可运行示例](log-mask-samples/README.md)
- [性能基准](log-mask-benchmarks/README.md)
- [安全策略](SECURITY.md)
- [贡献说明](CONTRIBUTING.md)
- [Apache License 2.0](LICENSE)
