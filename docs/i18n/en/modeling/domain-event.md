# Domain Events

A domain event is an explicit business fact that occurred inside an aggregate.
In JFoundry this is a domain-model primitive: recording the fact is not publishing
a message and is not writing an Outbox row.

jMolecules supplies the `DomainEvent` marker. JFoundry's convention is that an
aggregate records that marker through `recordEvent(...)` during its own business
behavior, and a runtime later drains the captured facts at an application-service
boundary.

## Model the fact

Extend `BaseDomainEvent`. The base type assigns `eventId` and `occurredAt`, and
implements equals/hashCode on `eventId` so consumers can deduplicate by identity
of the fact rather than object identity.

```java
public final class OrderConfirmed extends BaseDomainEvent {

    private final OrderId orderId;

    public OrderConfirmed(OrderId orderId) {
        this.orderId = orderId;
    }

    public OrderId getOrderId() {
        return orderId;
    }
}
```

Record the event from aggregate behavior. `recordEvent(...)` only captures the
fact; it does not dispatch, externalize, or persist it.

```java
public class Order extends BaseAggregateRoot<Order, OrderId> {

    public void confirm() {
        recordEvent(new OrderConfirmed(getId()));
    }
}
```

`EventRecordable.drainEvents()` is the framework-neutral handoff SPI. Automatic
runtimes invoke it after a successful outermost application-service boundary.
Business code on those runtimes does not call it.

## In-process dispatch

The default path stays inside the process:

1. Persistence or application code registers the touched aggregate with
   `DomainEventContext.register(...)`.
2. After the outermost `@ApplicationService` invocation succeeds, the runtime
   drains pending events.
3. Each `DomainEventDispatcher.dispatch(...)` receives the batch.

If that outermost invocation fails, pending events are not published. Runtime
pages describe when dispatch happens relative to the local transaction.

## Independent of Outbox

In-process listeners do not need Outbox. Compose the two only when a captured
domain event must leave the process reliably. That optional path uses
`@Externalized`, `DomainEventExternalizer`, `DomainEventOutboxRecorder`, or
`OutboxTemplate`, and is documented in
[Reliable Messaging](../capabilities/reliable-messaging.md).

Runtime dispatch wiring lives in the [Spring Boot](../implementations/spring-boot.md),
[Quarkus](../implementations/quarkus.md), and [Helidon MP](../implementations/helidon.md)
guides.
