# 可观测性

可观测性属于可选的外层适配器。JFoundry 的应用层契约不会创建 SDK、exporter、collector、采样器或资源属性。宿主应用负责这些生命周期，并选择遥测后端。

`jfoundry-observability-otel` 为四个框架操作提供 OpenTelemetry API 装饰器：

- `jfoundry.outbox.persist`
- `jfoundry.outbox.dispatch`
- `jfoundry.inbox.process`
- `jfoundry.lock.acquire`

装饰器为每次调用记录一个 span 以及 `jfoundry.operation.count` 计数器。它的属性只有固定操作名和有限 outcome，不会记录消息 ID、聚合 ID、topic、payload key、消费者名、锁 value 或异常文本。

```java
OpenTelemetryJFoundryObservability observations =
        new OpenTelemetryJFoundryObservability(openTelemetry);

OutboxRecorder recorder = observations.observe(new OutboxTemplate(store, serializer));
LockExecutor lockExecutor = observations.observe(LockExecutor.create(lockClient));
```

同一个操作只能组合一种观测实现。尤其不能再用该直接 OpenTelemetry 装饰器包裹已由 Spring Micrometer Observation 观测的操作，否则会产生重复的 span 和指标。

跨服务追踪只传播由 `OutboundMessage` 携带的有界 W3C trace context；它与指标和技术审计数据保持分离。
