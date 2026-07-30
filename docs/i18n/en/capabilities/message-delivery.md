# Message Delivery

Message delivery is the outbound transport capability. It is separate from
[reliable messaging](reliable-messaging.md): use this capability for an explicit producer send, and
add Outbox and Inbox only when a business change must be recorded for later delivery or a consumer
must be idempotent.

## Transport Contract

`MessageSender` is the runtime-neutral outbound port. It sends an `OutboundMessage` containing a
topic, optional payload key, string payload, and bounded propagation metadata, then returns a
`SendResult`. The core does not depend on a broker client or decide how an application handles a
failed send.

The application owns its wire payload and destination semantics. `PayloadSerializer` is the
separate serialization SPI used when an Outbox records an event; direct sends provide their payload
string explicitly. Keep business destination names and payload contracts outside the domain model.

## Direct And Reliable Delivery

Direct delivery asks the selected broker producer to publish immediately. It is appropriate when
the application can make its own retry, failure, and idempotency decisions. It does not atomically
persist a business change and a message, and it does not create a consumer idempotency record.

For transactional publication, mark an integration event for externalization or use
`OutboxTemplate`, then add a selected `MessageSender` transport for dispatch. For consumer-side
idempotency, add Inbox as well. The complete state, retry, and ownership semantics are defined by
[Reliable Messaging: Outbox And Inbox](reliable-messaging.md).

## Runtime Integrations

| Runtime | Built-in delivery adapters | Selection |
|---|---|---|
| Spring Boot | Kafka, RabbitMQ, RocketMQ | Add the matching `jfoundry-messaging-*-spring-boot-starter`. The base messaging starter provides no fallback sender. |
| Quarkus | Kafka, RabbitMQ | Add `jfoundry-messaging-kafka-quarkus-runtime` or `jfoundry-messaging-rabbitmq-quarkus-runtime`. |
| Helidon MP | None | Provide and verify an application-owned `MessageSender` for the selected Helidon client. |

Spring Boot application beans take precedence over the provided `MessageSender`. Quarkus supplies
its adapters as replaceable CDI defaults. No runtime integration infers a broker choice merely from
the presence of an Outbox.

See [Spring Boot Runtime Assembly](../implementations/spring-boot.md) for starter composition,
[Quarkus Runtime Integration](../implementations/quarkus.md) for its client configuration, and
[Helidon MP Runtime Integration](../implementations/helidon.md) for its current limitation.
