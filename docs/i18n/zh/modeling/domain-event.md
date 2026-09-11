# 领域事件

领域事件是聚合内部发生的显式业务事实。在 JFoundry 中，它是领域模型原语：记录该事实
既不是发布消息，也不是写入 Outbox 行。

jMolecules 提供 `DomainEvent` 标记。JFoundry 的约定是：聚合在自身业务行为中通过
`recordEvent(...)` 记录该标记，运行时再在应用服务边界提取已收集的事实。

## 如何建模

继承 `BaseDomainEvent`。基类会分配 `eventId` 与 `occurredAt`，并按 `eventId` 实现
equals/hashCode，因此消费者按事实标识去重，而不是按对象身份去重。

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

在聚合行为中记录事件。`recordEvent(...)` 只收集事实，不会派发、外部化或持久化它。

```java
public class Order extends BaseAggregateRoot<Order, OrderId> {

    public void confirm() {
        recordEvent(new OrderConfirmed(getId()));
    }
}
```

`EventRecordable.drainEvents()` 是运行时无关的交接 SPI。自动运行时会在对应派发阶段调用它。这些运行时上的业务代码不调用该方法。

运行时生命周期适配器只依赖一个 `DomainEventDispatchCoordinator`。默认协调器可以把整批事件路由到多个
`DomainEventDispatcher` 实现，同时将提交前与提交后的行为隐藏在这个单一边界之后。

## 进程内分发

默认路径停留在进程内：

1. 运行时通过 `AggregateEventRegistrar` 把 `AbstractAggregateRepository` 接到领域事件上下文。未引入 domain-event 的应用会跳过该注册。应用代码仍可直接调用 `DomainEventContext.register(...)`。
2. `register(...)` 只允许在 `@ApplicationService` 调用内使用；作用域外立即失败。
3. 存在活动事务时，作用域把已注册聚合放到事务资源上。Outbox 派发器（`BeforeCommitDomainEventDispatcher`）在 `beforeCommit` / `beforeCompletion` 触发；普通进程内派发器在 `afterCommit` / `afterCompletion(STATUS_COMMITTED)` 触发。拦截器不会派发这些事务绑定的事件。
4. 没有活动事务时，最外层 `@ApplicationService` 成功完成后，会把整批事件交给每一个派发器。

若最外层调用失败，待分发事件不会被发布。派发器被调用时立即同步触发，不会再等待后续事务阶段。

## 与 Outbox 相互独立

进程内监听不需要 Outbox。只有已收集的领域事件必须可靠离开本进程时，才把两者组合起来。
该可选路径使用 `@Externalized`、`DomainEventExternalizer`、`DomainEventOutboxRecorder`
或 `OutboxTemplate`，详见[可靠消息](../capabilities/reliable-messaging.md)。

运行时分发装配见 [Spring Boot](../implementations/spring-boot.md)、
[Quarkus](../implementations/quarkus.md) 与 [Helidon MP](../implementations/helidon.md)
指南。
