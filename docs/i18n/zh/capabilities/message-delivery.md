# 消息传输

消息传输是出站传输能力，与[可靠消息](reliable-messaging.md)分离：需要显式生产者发送时使用本能力；只有业务变更必须在之后可靠投递，或消费者必须具备幂等性时，才额外选择 Outbox 和 Inbox。

## 传输契约

`MessageSender` 是运行时无关的出站端口。它发送包含 topic、可选 payload key、字符串 payload 和有界传播元数据的 `OutboundMessage`，并返回 `SendResult`。核心不依赖任何消息代理客户端，也不替应用决定发送失败后的处理策略。

应用拥有线上的 payload 与目的地语义。`PayloadSerializer` 是 Outbox 记录事件时使用的独立序列化 SPI；直接发送由调用方显式提供 payload 字符串。业务目的地名称和消息契约不应进入领域模型。

## 直接发送与可靠投递

直接发送会立刻要求所选消息代理生产者发布消息。它适用于应用能够自行决定重试、失败处理和幂等策略的场景。它不会原子地持久化业务变更与消息，也不会创建消费者幂等记录。

需要事务性发布时，为集成事件标注外部化，或使用 `OutboxTemplate`，再为派发选择一个 `MessageSender` 传输实现。需要消费端幂等时，再加入 Inbox。完整的状态、重试与所有权语义见[可靠消息：Outbox 与 Inbox](reliable-messaging.md)。

## 运行时集成

| 运行时 | 内置传输适配器 | 选择方式 |
|---|---|---|
| Spring Boot | Kafka、RabbitMQ、RocketMQ | 加入对应的 `jfoundry-messaging-*-spring-boot-starter`。基础消息启动器不会提供回退发送器。 |
| Quarkus | Kafka、RabbitMQ | 加入 `jfoundry-messaging-kafka-quarkus-runtime` 或 `jfoundry-messaging-rabbitmq-quarkus-runtime`。 |
| Helidon MP | 无 | 为所选 Helidon 客户端提供并验证应用自有的 `MessageSender`。 |

Spring Boot 中应用提供的 `MessageSender` Bean 优先于内置实现。Quarkus 将适配器作为可替换的 CDI 默认 Bean 提供。没有任何运行时集成会仅因存在 Outbox 就推断消息代理选择。

启动器组合见 [Spring Boot 运行时装配](../implementations/spring-boot.md)，Quarkus 客户端配置见
[Quarkus 运行时集成](../implementations/quarkus.md)，Helidon 当前限制见
[Helidon MP 运行时集成](../implementations/helidon.md)。
