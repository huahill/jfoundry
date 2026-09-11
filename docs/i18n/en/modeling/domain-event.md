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
runtimes invoke it at the dispatch phase. Business code on those runtimes does
not call it.

Runtime lifecycle adapters depend on one `DomainEventDispatchCoordinator`. The
default coordinator may route a batch to multiple `DomainEventDispatcher`
implementations, while keeping before-commit and after-commit behavior behind
that single boundary.

## In-process dispatch

The default path stays inside the process:

1. Runtime wiring binds `AbstractAggregateRepository` through
   `AggregateEventRegistrar`. Applications without domain-event skip that
   registration. Application code may still call
   `DomainEventContext.register(...)` directly.
2. `register(...)` is legal only inside an `@ApplicationService` invocation.
   Calling it outside that scope fails immediately.
3. When a transaction is active, the scope stores registered aggregates on the
   transaction resource. Outbox dispatchers
   (`BeforeCommitDomainEventDispatcher`) fire in `beforeCommit` /
   `beforeCompletion`. Ordinary in-process dispatchers fire in `afterCommit` /
   `afterCompletion(STATUS_COMMITTED)`. The interceptor does not dispatch those
   transaction-bound events.
4. When no transaction is active, the outermost successful `@ApplicationService`
   invocation dispatches the full batch to every dispatcher.

If that outermost invocation fails, pending events are not published. Dispatchers
fire synchronously when called; they do not wait for a later transaction phase.

## Independent of Outbox

In-process listeners do not need Outbox. Compose the two only when a captured
domain event must leave the process reliably. That optional path uses
`@Externalized`, `DomainEventExternalizer`, `DomainEventOutboxRecorder`, or
`OutboxTemplate`, and is documented in
[Reliable Messaging](../capabilities/reliable-messaging.md).

Runtime dispatch wiring lives in the [Spring Boot](../implementations/spring-boot.md),
[Quarkus](../implementations/quarkus.md), and [Helidon MP](../implementations/helidon.md)
guides.
