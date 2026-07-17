# log-mask JMH benchmarks

本模块只由根项目的 `benchmarks` Maven profile 加入 reactor。它保留
`maven.install.skip` 与 `maven.deploy.skip`，不会进入默认构建、共享 CI 或发布物。

## 场景

所有场景都通过相同的内存 `ClientHttpRequestFactory` 执行本地 HTTP 调用，并由
`RestTemplate` 正常关闭和消费响应；没有网络、磁盘或控制台 I/O。logging 开启时，
`log.mask.http` 被显式设为 INFO，并连接 Logback 的 `NOPAppender`；同时将 Spring 调试 logger
压到 WARN。因此仍会执行事件采集、治理和 JSON 生成，只丢弃最终日志事件，且不让框架调试输出
主导结果。

| JMH 方法 | 场景 |
| --- | --- |
| `nativeRestTemplate` | 不含 starter 与观测链的原生无 body `RestTemplate` 调用。 |
| `starterPresentLoggingDisabled` | starter/自动配置存在，`logging.enabled=false`，确认未安装观测 interceptor。 |
| `loggingEnabledNoBody` | logging 开启的无 body GET/204 调用。 |
| `loggingEnabledTypedDtoOneKiB` | logging 开启的 1 KiB 类型化 JSON DTO POST/响应消费。 |
| `loggingEnabledTypedDtoSixtyFourKiB` | logging 开启的 64 KiB 类型化 JSON DTO POST/响应消费。 |

两种 DTO 在每次 trial 的 setup 中以实际最终 UTF-8 JSON 字节数校验为 1024 和 65536，
以防 fixture 漂移。64 KiB 场景正好覆盖默认日志体预算边界。

## 构建、发现与烟雾运行

在仓库根目录执行：

```powershell
mvn -Pbenchmarks -pl log-mask-benchmarks -am clean package
java -jar log-mask-benchmarks/target/benchmarks.jar -l
java -jar log-mask-benchmarks/target/benchmarks.jar RestTemplateObservationBenchmark -wi 0 -i 1 -f 1 -w 100ms -r 100ms -foe true
```

第二个命令仅用于快速发现并执行全部五个场景；它有意缩短/覆盖正式测量配置，不能用于性能比较。

## 正式结果

基准注解固定为单线程、5 次 1 秒预热、5 次 1 秒测量、2 个 fork，并为每个 fork 固定
`-Xms512m -Xmx512m -XX:+UseG1GC`。使用 JMH 内置 `gc` profiler 报告吞吐量和每操作分配：

```powershell
java -jar log-mask-benchmarks/target/benchmarks.jar RestTemplateObservationBenchmark -prof gc -rf json -rff log-mask-benchmarks/target/jmh-result.json
```

结果 JSON 的每个方法包含 `primaryMetric`（`ops/s`）及
`secondaryMetrics.gc.alloc.rate.norm`（`B/op`）。保存该文件以及以下环境信息，再与同一环境的
上一版结果进行人工比较；本模块不为单台开发机设置绝对通过阈值：

```powershell
java -version
mvn -version
Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors
```
