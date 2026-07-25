# 可靠消息：Outbox 与 Inbox

只有领域事件必须可靠投递到其他进程或外部系统时，才使用 Transactional Outbox。进程内事件处理不需要它。Inbox 为一条消息与一个消费者的组合提供消费端幂等。

![transactional-outbox.png](../../assets/outbox/transactional-outbox.png)

## 事件流

```text
聚合记录领域事件
  -> 应用服务边界提取事件
  -> 外部化选择主题、键和消息载荷
  -> 与业务变更在同一数据库事务写入 Outbox 行
  -> 派发器领取后经由 MessageSender 发送
  -> 消费端使用 InboxTemplate 实现幂等
```

自动外部化只记录被明确标记为外部化的事件；路由元数据本身不会使事件外部化。只有事件本身是稳定公共契约时才使用自动外部化；否则在应用边界翻译为版本化集成契约，并使用 `OutboxTemplate` 显式追加。该模板加入调用方事务，不会自行开启事务或同步发送。

## Payload 契约

将 `payloadType` 视为稳定的契约名称，而不是 Java 类名。消费者应将消息信封反序列化为各自的版本化契约。应选择保持线格式可移植且不暴露 JVM 类型名的消息载荷序列化器。

## Outbox 状态机

- `PENDING`：已写入，等待派发。
- `DISPATCHING`：已被派发器领取。
- `PUBLISHED`：发送成功。
- `FAILED`：本次发送失败，等待重试。
- `DEAD_LETTERED`：超过最大重试次数。

Recovery 将卡住的 `DISPATCHING` 消息恢复为 `PENDING`。Cleanup 只删除过期终态记录。运行时派发触发和维护任务调度属于实现关注点。

## 运行时事务边界

`OutboxTemplate.append(...)` 加入业务事务，不会自行开启独立事务。Spring Boot 运行时的派发则使用三个独立的短数据库事务：领取记录、在数据库事务外发送每条已领取的消息载荷、再记录发送结果。恢复和每个清理批次也在独立事务中执行。JPA 和 MyBatis-Plus 存储均遵循这一语义。

`InboxTemplate` 先在新事务中领取消息。处理器与 `PROCESSED` 状态迁移在第二个独立事务中执行。处理器失败时，该事务回滚，新的事务会记录 `FAILED`，然后重新抛出原始异常。只有存在 `TransactionRunner` 时，Boot 才会创建具备该语义的模板。直接使用 `new InboxTemplate(store)` 属于手工运行时 API，调用方必须为存储提供所需的事务边界。

## SQL 模板

SQL 仅作为可复制模板提供，jfoundry 从不自动执行。`jfoundry-outbox-core` 拥有规范 Outbox 路径，`jfoundry-inbox-core` 拥有规范 Inbox 路径。将需要的模板复制进业务应用的迁移流程：

```text
jfoundry/sql/outbox/mysql/create_outbox_event.sql
jfoundry/sql/outbox/postgresql/create_outbox_event.sql
jfoundry/sql/inbox/common/create_inbox_message.sql
```

## 选择存储实现

| 需求 | 指南 |
|------|------|
| MyBatis-Plus Outbox 和 Inbox 存储 | [MyBatis-Plus](../implementations/mybatis-plus.md) |
| JPA Outbox 和 Inbox 存储，包括数据库相关的 Inbox 领取策略 | [JPA](../implementations/jpa.md) |
| Quarkus Outbox 运行时、自动领域事件外部化与 Kafka 投递 | [Quarkus](../implementations/quarkus.md) |
| Spring Boot 能力装配和派发器配置 | [Spring Boot](../implementations/spring-boot.md) |

启动器、配置项和注册条件查询请使用 [Spring Boot 自动配置](../reference/spring-boot-autoconfiguration.md)。
