# Spring Boot 自动配置总览

本文是 Spring Boot 入口、配置项和 Bean 装配条件的查询材料。行为契约请先看[能力页](../capabilities/aggregate-persistence.md)，技术相关配置请看[实现指南](../implementations/spring-boot.md)。

## 启动器入口

| 启动器 | 引入能力 | 不会引入 |
|---------|----------|----------|
| `jfoundry-spring-boot-starter` | Spring Boot 自动配置、Spring `TransactionRunner` 集成 | Outbox、Inbox、MyBatis-Plus 存储、消息代理客户端、JobRunr |
| `jfoundry-observability-spring-boot-starter` | 对符合条件的 Outbox、Inbox 和锁操作进行 Micrometer Observation | 遥测 exporter、collector 或直接 OpenTelemetry 装饰器 |
| `jfoundry-lock-redisson-spring-boot-starter` | 分布式锁核心、Spring `@DistributedLock` 拦截、Redisson 适配器、Redisson Spring Boot 启动器 | Outbox、Inbox、消息代理投递 |
| `jfoundry-event-spring-boot-starter` | 领域事件派发、Spring 应用事件发布 | Outbox 持久化或消息代理投递 |
| `jfoundry-messaging-spring-boot-starter` | 消息 SPI、Jackson 消息载荷序列化器和 Spring 消息运行时 | 任意 `MessageSender` 或消息代理客户端 |
| `jfoundry-messaging-kafka-spring-boot-starter` | Kafka `MessageSender` 适配器，在 Boot 创建 `KafkaOperations` 后选择 | Outbox 存储 |
| `jfoundry-messaging-rabbitmq-spring-boot-starter` | RabbitMQ `MessageSender` 适配器 | Outbox 存储 |
| `jfoundry-messaging-rocketmq-spring-boot-starter` | RocketMQ `MessageSender` 适配器 | Outbox 存储 |
| `jfoundry-outbox-spring-boot-starter` | Outbox 核心、`OutboxTemplate`、领域事件外部化、定时派发集成 | Outbox 表存储、JobRunr |
| `jfoundry-outbox-mybatis-plus-spring-boot-starter` | MyBatis-Plus `OutboxMessageStore` 适配器 | 数据库迁移执行 |
| `jfoundry-outbox-jpa-spring-boot-starter` | JPA `OutboxMessageStore` 适配器 | 数据库迁移执行 |
| `jfoundry-outbox-jobrunr-spring-boot-starter` | JobRunr Outbox 派发器 | Outbox 表存储 |
| `jfoundry-inbox-spring-boot-starter` | Inbox 核心、`InboxTemplate` | Inbox 表存储 |
| `jfoundry-inbox-mybatis-plus-spring-boot-starter` | MyBatis-Plus `InboxMessageStore` 适配器 | 数据库迁移执行 |
| `jfoundry-inbox-jpa-spring-boot-starter` | JPA `InboxMessageStore` 适配器和受支持数据库的领取策略 | 数据库迁移执行，以及 PostgreSQL、MySQL 之外数据库的内置领取支持 |
| `jfoundry-persistence-mybatis-plus-spring-boot-starter` | 业务 MyBatis-Plus 持久化入口：基础自动配置、共享持久化运行时支持和 MyBatis-Plus Boot 启动器 | 运行时无关的 `jfoundry-persistence-mybatis-plus-starter`、Outbox/Inbox 存储 |
| `jfoundry-persistence-jpa-spring-boot-starter` | 每个聚合一个由 JPA 管理的实体图的 jfoundry JPA 适配器、共享 Spring 事务持久化上下文、Spring Boot JPA 运行时 | 对分离聚合执行合并、手动多表或多实体图同步算法、Outbox 和 Inbox 存储 |
| `jfoundry-webmvc-spring-boot-starter` | Web MVC `ProblemDetail` 异常响应 | 消息、Outbox、Inbox |

## 配置项

| 配置项 | 默认值 | 作用 |
|--------|--------|------|
| `jfoundry.lock.annotation.enabled` | `true` | 当存在 `DistributedLockClient` Bean 时，开启 `@DistributedLock` advisor。 |
| `jfoundry.domain.event.dispatch.enabled` | `true` | 开启应用服务边界上的领域事件自动派发。 |
| `jfoundry.domain.event.dispatch.spring.enabled` | `true` | 当 Spring 事件适配器存在时，开启 Spring `ApplicationEventPublisher` 派发。 |
| `jfoundry.domain.event.dispatch.outbox.enabled` | `false` | 当存在 `DomainEventOutboxRecorder` Bean 时，开启 Outbox 领域事件派发。 |
| `jfoundry.outbox.table-name` | `jfoundry_outbox_event` | 改写 MyBatis-Plus Outbox 物理表名。业务应用必须自行建表。 |
| `jfoundry.outbox.dispatcher.mode` | `scheduled` | 选择 `scheduled`、`jobrunr` 或 `none`。 |
| `jfoundry.outbox.dispatcher.interval-ms` | `5000` | 定时派发固定延迟间隔。 |
| `jfoundry.outbox.dispatcher.cron` | `*/10 * * * * *` | JobRunr 周期性派发 cron 表达式。 |
| `jfoundry.outbox.dispatcher.batch-size` | `50` | 每次派发最多领取的记录数。 |
| `jfoundry.outbox.dispatcher.max-retries` | `5` | 进入死信前最大派发尝试次数。 |
| `jfoundry.outbox.dispatcher.backoff-base-ms` | `1000` | 重试退避基础值。 |
| `jfoundry.outbox.dispatcher.backoff-max-ms` | `300000` | 最大重试退避值。 |
| `jfoundry.outbox.recovery.enabled` | 跟随派发模式 | 在 `scheduled` 和 `jobrunr` 下开启卡住的 `DISPATCHING` 恢复；`none` 下始终关闭。 |
| `jfoundry.outbox.recovery.interval` | `60s` | 恢复任务间隔。 |
| `jfoundry.outbox.recovery.stuck-timeout` | `5m` | `DISPATCHING` 记录超过该时间后视为卡住。 |
| `jfoundry.outbox.cleanup.enabled` | 跟随派发模式 | 在 `scheduled` 和 `jobrunr` 下开启终态记录清理；`none` 下始终关闭。 |
| `jfoundry.outbox.cleanup.interval` | `24h` | 清理任务间隔。 |
| `jfoundry.outbox.cleanup.published-retention-days` | `7` | `PUBLISHED` 记录保留天数。 |
| `jfoundry.outbox.cleanup.dead-lettered-retention-days` | `30` | `DEAD_LETTERED` 记录保留天数。 |
| `jfoundry.outbox.cleanup.batch-size` | `1000` | 每批最多删除记录数。 |

## 自动配置条件

| 自动配置 | 注册 Bean | 主要条件 |
|----------|-----------|----------|
| `JFoundryAopAutoConfiguration` | Spring 规范的内部自动代理创建器 | Spring AOP 可用。领域事件和分布式锁顾问共用一个自动代理创建器，并延迟解析各自的拦截器。 |
| `TransactionRunnerAutoConfiguration` | `SpringTransactionRunner` | 存在 `TransactionRunner` 与 `TransactionTemplate`，Spring Boot 已配置 `PlatformTransactionManager`，且没有已有 `TransactionRunner`。 |
| `DistributedLockAutoConfiguration` | `LockExecutor`、可选 Redisson `DistributedLockClient`、可选 `@DistributedLock` 顾问 | 存在 `jfoundry-lock-core`。Redisson 适配器需要 `RedissonClient`；注解顾问需要 `DistributedLockClient` 且开启注解支持。 |
| `MicrometerObservationAutoConfiguration` | 原始 JFoundry 操作 Bean 的 Micrometer 顾问 | 存在 `ObservationRegistry`（使用可观测性启动器时由 Actuator 提供）；存在 Micrometer Observation、Spring AOP，以及至少一个符合条件的 Outbox、Inbox 或锁操作 Bean。 |
| `DomainEventPersistenceAutoConfiguration` | Repository `DomainEventContext` 注入器 | 类路径中存在 `DomainEventContext` 和 `AbstractAggregateRepository`。 |
| `PersistenceFailureAutoConfiguration` | 默认 Spring `PersistenceFailureTranslator` 与 Repository 注入器 | 存在 `AbstractAggregateRepository`、Spring 数据访问异常和 `jfoundry-persistence-spring`；没有用户自定义翻译器。 |
| `AggregatePersistenceContextAutoConfiguration` | 事务绑定的 `AggregatePersistenceContext` 与感知型 Repository 注入器 | 存在持久化上下文 SPI、Spring 事务支持和 `jfoundry-persistence-spring`；没有用户自定义上下文。 |
| `DomainEventDispatchAutoConfiguration` | `DomainEventScope`、`DomainEventContext`、派发拦截器、Spring 事件派发器、可选 Outbox 派发器 | 应用服务和派发器类型存在；配置项允许对应路径。 |
| `DomainEventOutboxRecorderAutoConfiguration` | `PayloadSerializer`、`OutboxTemplate`、外部化解析器、`DomainEventOutboxRecorder` | Outbox 存储和序列化器依赖可用；每种 Bean 均没有用户自定义替代。 |
| `KafkaMessageSenderAutoConfiguration` | `SpringKafkaMessageSender` | 存在 `KafkaOperations` 类和 Bean；没有已有 `MessageSender`。 |
| `RabbitMqMessageSenderAutoConfiguration` | `SpringRabbitMqMessageSender` | 存在 `RabbitTemplate` 类和 `RabbitOperations` Bean；没有已有 `MessageSender`。 |
| `RocketMqMessageSenderAutoConfiguration` | `SpringRocketMqMessageSender` | 存在 RocketMQ 生产者类和 `MQProducer` Bean；没有已有 `MessageSender`。 |
| `OutboxMybatisPlusAutoConfiguration` | Outbox 表名定制器、`MybatisPlusInterceptor`、`OutboxMessageStore` | MyBatis-Plus 和 Outbox 存储适配器类存在。SQL 模板不会自动执行。 |
| `OutboxJpaAutoConfiguration` | JPA `OutboxMessageStore` | 存在 `EntityManagerFactory` 和 JPA Outbox 适配器；没有用户自定义 `OutboxMessageStore`。 |
| `OutboxDispatcherAutoConfiguration` | `BackoffStrategy`、定时派发器、恢复任务、清理任务 | 存在 Outbox 存储、消息发送器和 `TransactionRunner`；模式为 `scheduled` 或维护任务由托管模式启用。 |
| `JobRunrDispatcherAutoConfiguration` | `JobRunrOutboxDispatcher` | 存在 JobRunr 和 jfoundry JobRunr 适配器类；`mode=jobrunr`；存在存储、发送器、退避策略和 `TransactionRunner` Bean。 |
| `InboxMybatisPlusAutoConfiguration` | MyBatis-Plus `InboxMessageStore` | 存在 `SqlSessionFactory`、映射器扫描和 Inbox 存储适配器；没有已有存储。 |
| `InboxJpaAutoConfiguration` | `JpaInboxClaimStrategy`、JPA `InboxMessageStore` | 存在 `EntityManagerFactory` 和 JPA Inbox 适配器。用户提供的 `InboxMessageStore` 或 `JpaInboxClaimStrategy` 优先；内置领取策略仅支持 PostgreSQL 和 MySQL，未知数据库产品在应用未提供策略时会快速失败。 |
| `InboxAutoConfiguration` | `InboxTemplate` | 类路径中存在 `InboxTemplate`，且存在 `InboxMessageStore` 和 `TransactionRunner` Bean。 |
| `WebMvcProblemDetailAutoConfiguration` | `ProblemDetailExceptionHandler` | Servlet Web MVC 应用且处理器类存在；没有已有处理器。 |

## 说明

- Kafka 发送器自动配置在 Spring Boot 的 `KafkaAutoConfiguration` 之后执行，因此 jfoundry 评估
  发送器条件时可以看到 Boot 创建的 `KafkaOperations` Bean。
- jfoundry 不注册回退 `MessageSender`。启用 Outbox 投递前，应添加消息代理专用启动器或
  定义应用自己的 `MessageSender`。
- `TransactionRunnerAutoConfiguration` 在 Spring Boot 事务自动配置之后运行，确保其 Bean 条件评估前已经可以看到 JDBC、JPA 或 JTA 事务管理器。
- jfoundry 通过 Spring 规范的自动代理创建器注册各类顾问。其他 Spring 集成已经注册更强的
  创建器时，Spring 的标准升级协议会保留该创建器；业务应用无需注册 jfoundry 专用代理创建器。
- 分布式锁是显式能力。默认 Spring Boot 启动器不会引入 Redisson。
- `mode=none` 表示不注册派发器、恢复任务或清理任务，即使显式开启恢复或清理也不会注册。
