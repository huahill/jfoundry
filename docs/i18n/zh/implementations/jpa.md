# JPA 实现

该实现使用 Jakarta Persistence 满足[聚合持久化](../capabilities/aggregate-persistence.md)和[可靠消息](../capabilities/reliable-messaging.md)契约。

## 聚合持久化

受支持的默认形态是每个聚合一个由 JPA 管理的实体图。`JpaAggregateRepository` 通过 `EntityManager.find` 加载实体图，追踪受管理的根实体，把聚合变更应用到同一个图，并在报告成功前 flush。它从不调用 `merge`；加载、领域行为和 `modify(...)` 必须位于同一个事务和持久化上下文。

`JpaAggregateMapper` 创建和还原实体图、转换 ID，并把当前聚合状态同步到受管理的实体图。它不提供跨多个表或实体图的通用手工同步。

乐观并发控制时，在实体图根实体上标注 `@Version`，并确保每次实体图变更都会改变根实体的持久化属性。子实体变更不会仅因根实体存在 `@Version` 就参与并发控制；映射器可在同步子实体变更时显式触及根实体。并发根更新会在仓储刷新时以 `ConflictException` 报告。

JPA 业务运行时装配使用 `jfoundry-persistence-jpa-spring-boot-starter`。它不会引入 Outbox 或 Inbox 存储。

Quarkus 环境中，加入 `jfoundry-transaction-quarkus-runtime`、`jfoundry-persistence-quarkus-runtime`、
`jfoundry-persistence-jpa`、
`jfoundry-persistence-jpa-quarkus-runtime`、Quarkus Hibernate ORM 和所选数据源扩展即可。由 CDI 管理的
`JpaAggregateRepository` 会从 jfoundry Quarkus 扩展获得事务作用域内的
`AggregatePersistenceContext`。应使用 `TransactionRunner` 作为事务边界，业务代码无需创建或设置该上下文。
JPA Quarkus capability 会翻译已知 Hibernate 可用性失败，应用可以使用自己的 CDI
`PersistenceFailureTranslator` 覆盖它。
运行时装配要求见 [Quarkus](quarkus.md)。

Helidon MP 环境中，加入 `jfoundry-persistence-jpa-helidon`，再配置应用自己的 Helidon CDI
JPA/Hibernate 集成、数据源和持久化单元。该构件会引入 `jfoundry-persistence-jpa` 与
`jfoundry-persistence-helidon`；后者提供事务绑定的持久化上下文，Helidon JPA 构件则补充已识别的
Hibernate 可用性失败转换。
显式依赖组合和原生镜像限制见 [Helidon MP](helidon.md)。

### 直接装配 JPA 或 Hibernate

JPA 适配器是运行时无关的，但不是开箱即用的原生 Hibernate 引导方案。Spring Boot、受支持的 Quarkus 和 Helidon
装配之外，应用负责创建 `EntityManagerFactory`、开启和完成每个事务、向持久化单元注册聚合与框架实体，并向
`JpaAggregateRepository` 提供事务作用域内的 `AggregatePersistenceContext`。仓储只能在该持久化上下文和事务内使用。

JPQL 是面向实体及其字段的可移植查询语言。Hibernate 会把普通 JPQL 查询和更新转换为所选数据库方言。Inbox 的首次领取刻意不同：它需要原子的“插入或忽略”等价操作，因此必须显式保留数据库相关策略，而不能假装 JPQL 能可移植地表达该操作。

## Outbox 与 Inbox 存储

显式选择 `jfoundry-outbox-jpa-spring-boot-starter` 和/或 `jfoundry-inbox-jpa-spring-boot-starter`。JPA 适配器是运行时无关的；在 Spring Boot 下，框架实体会自动映射，因此应用无需把它们加入自己的 `@EntityScan`。

Quarkus 的 JPA Outbox 存储加入 `jfoundry-outbox-jpa-quarkus-runtime`。它会把
`JpaOutboxMessageEntity` 注册到默认 persistence unit，并提供默认 CDI `OutboxMessageStore`；应用提供的 CDI
`OutboxMessageStore` 优先。此项仅为存储装配，不会加入派发器、调度器、序列化器、自动事件外部化或 Inbox
支持。依赖配置和原生镜像验证见 [Quarkus](quarkus.md)。

Quarkus 的 Outbox 派发加入 `jfoundry-outbox-quarkus-runtime`，并提供真实的 CDI `MessageSender`。
只有设置 `jfoundry.outbox.dispatcher.enabled=true` 后调度才会启用；它不会引入消息代理客户端或回退发送器。
派发配置见 [Quarkus](quarkus.md)。

Quarkus 的 JPA Inbox 存储加入 `jfoundry-inbox-jpa-quarkus-runtime`。它会把
`JpaInboxMessageEntity` 注册到默认 persistence unit，并提供默认 CDI `JpaInboxClaimStrategy`、
`InboxMessageStore` 和 `InboxTemplate` Bean。默认领取策略只支持 PostgreSQL 与 MySQL；其他数据库应声明
`JpaInboxClaimStrategy`。应用提供的 `JpaInboxClaimStrategy`、`InboxMessageStore` 或 `InboxTemplate` 优先。
此项仅为存储装配，不会加入派发器、调度器、序列化器、自动事件外部化或启动器。依赖配置和原生镜像
验证见 [Quarkus](quarkus.md)。

Helidon MP 中，加入 `jfoundry-outbox-jpa-helidon` 可暴露 JPA `OutboxMessageStore`；加入
`jfoundry-inbox-jpa-helidon` 可暴露 `JpaInboxClaimStrategy`、`InboxMessageStore` 和
`InboxTemplate`。两者都要求应用提供 JPA 集成和迁移。若要启用定时派发和自动事件外部化，还需单独加入
`jfoundry-outbox-helidon`。CDI 替代实现的替换规则与原生镜像范围见 [Helidon MP](helidon.md)。

实体注册不等于 schema 管理。将匹配的 Outbox 或 Inbox SQL 模板复制到应用自己的迁移流程并在那里维护；不要依赖 Hibernate schema 自动生成功能创建或演进 jfoundry 表。

Outbox 存储使用 JPQL 读取一页可派发候选记录，并通过比较并设置更新逐条领取。派发器的领取令牌建立所有权；已领取记录的发布和失败更新都使用该令牌。

JPA Inbox 内置原子领取策略只支持 PostgreSQL 和 MySQL。其他数据库产品需要提供 `JpaInboxClaimStrategy`。当数据库产品未知且未提供策略时，运行时默认实现会快速失败，而不是选择通用方言行为。用户提供的 `InboxMessageStore`、`OutboxMessageStore`、`InboxTemplate` 或 `JpaInboxClaimStrategy` 优先。

自定义领取策略实现 `boolean tryClaim(EntityManager entityManager, String messageId, String consumerName, String claimToken, Instant now)`。它必须以给定的令牌和领取时间原子创建 `PROCESSING` 行，并在消息已存在时返回 `false`；目标数据库必须具有并发重复投递测试。原生 JPA/Hibernate 的 Outbox 和 Inbox 装配必须应用[可靠消息](../capabilities/reliable-messaging.md)描述的事务边界。

运行时装配和配置见 [Spring Boot](spring-boot.md)、[Quarkus](quarkus.md)、[Helidon MP](helidon.md)和[自动配置参考](../reference/spring-boot-autoconfiguration.md)。
