# Spring Boot 运行时装配

Spring Boot 是运行时无关 jfoundry core 的对等运行时集成。它通过 Spring Boot starter 和条件化自动配置组装已选择的能力，不会让 Spring API 进入领域或应用模型；基础 starter 也不代表自动启用全部能力。

## 装配模型

在运行时装配模块导入用于 JFoundry 模块版本的 `jfoundry-dependencies`，以及用于 Spring
平台的 `jfoundry-spring-dependencies`，再添加 `jfoundry-spring-boot-starter`。基础 starter
保持轻量：它提供通用 Boot 装配和基于 Spring 的 `TransactionRunner`，但不引入持久化提供方、broker、Outbox、Inbox、JobRunr 或 Redisson client。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

其它能力都需要显式选择，从而使应用的数据源、投递、调度和分布式锁决策保持清晰。

## 能力组合

| 需求 | 添加 | 边界 |
|---|---|---|
| 本地应用事务 | `jfoundry-spring-boot-starter` | 提供 Spring `TransactionRunner`，应用可替换。 |
| 本地领域事件监听 | `jfoundry-event-spring-boot-starter` | 通过 Spring application event 发布领域事件；不是 Outbox 或 broker。 |
| MyBatis-Plus 聚合持久化 | `jfoundry-persistence-mybatis-plus-spring-boot-starter` | 仅业务聚合持久化，不含 Outbox/Inbox store。 |
| JPA 聚合持久化 | `jfoundry-persistence-jpa-spring-boot-starter` | 每个聚合一个受管实体图，不含 Outbox/Inbox store。 |
| RFC 9457 Web MVC 错误响应 | `jfoundry-webmvc-spring-boot-starter` | 仅 HTTP 入站适配。 |
| JSON 序列化契约 | `jfoundry-messaging-spring-boot-starter` | 提供 Spring messaging 集成和默认 Jackson `PayloadSerializer`，不提供真实 sender。 |
| Kafka、RabbitMQ 或 RocketMQ 投递 | 对应 `jfoundry-messaging-*-spring-boot-starter` | 显式选择具体 broker transport。 |
| Outbox runtime | `jfoundry-outbox-spring-boot-starter` | 提供外部化和 Spring 调度集成；store 与 sender 需另选。 |
| JPA 或 MyBatis-Plus Outbox store | 对应 `jfoundry-outbox-*-spring-boot-starter` | 只提供数据库 store；迁移由应用负责。 |
| Inbox runtime 与 store | `jfoundry-inbox-spring-boot-starter` 加一个 `jfoundry-inbox-*-spring-boot-starter` | 消费端幂等；迁移由应用负责。 |
| JobRunr Outbox 调度 | `jfoundry-outbox-jobrunr-spring-boot-starter` | 可选 dispatcher，仍需要 Outbox store 和真实 sender。 |
| Redisson 分布式锁 | `jfoundry-lock-redisson-spring-boot-starter` | 仅可选的跨实例锁能力。 |

完整 starter 清单、配置项、条件和 Bean 优先级见 [Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)。

## 事务与领域事件

优先使用运行时无关的 `TransactionRunner` 表达可移植的应用事务边界。Spring 将该契约映射到其事务基础设施并支持六种 jfoundry 传播模式。当应用明确选择 Spring 语义时，也可以使用 Spring `@Transactional`；不要在同一用例上叠加彼此独立的事务边界，除非已明确其所有权规则。详见[应用事务](../capabilities/application-transactions.md)。

事件 starter 会启用应用服务领域事件分发，并通过 Spring `ApplicationEventPublisher` 发布已分发的事件。普通 listener 在进程内观察发布；`@TransactionalEventListener` 可选择 `AFTER_COMMIT` 等事务阶段。这与 Outbox 路径不同。应用服务调用失败时，待分发的聚合事件不会被发布。

## 持久化

持久化 starter 的名称表达它们装配的能力，而不只是引入的 ORM。`jfoundry-persistence-jpa-spring-boot-starter` 装配 JPA 聚合 adapter、Spring 事务绑定的 persistence context 和 Spring Boot JPA runtime；MyBatis-Plus 对应 starter 则装配业务聚合的 MyBatis-Plus persistence。

二者都与可靠消息 store 明确分离。只有当用例需要可靠外部发布或消费端幂等时，才选择对应 Outbox 或 Inbox starter。聚合映射、乐观锁和 repository 形态见 [JPA](jpa.md) 与 [MyBatis-Plus](mybatis-plus.md) 实现指南。

## 可靠消息

Outbox starter 按配置模式提供事务感知的记录、调度投递、恢复和清理。它不会创建数据库表，也不会虚构消息目的地；将所选 SQL 模板复制到应用自己的迁移流程中。

`jfoundry-messaging-spring-boot-starter` 不会注册回退 `MessageSender`。启用投递前，必须添加一个 broker 专用 starter 或提供应用 `MessageSender`，否则不存在生产投递路径。自动领域事件外部化默认关闭，只有当被标注的领域事件有意作为稳定集成契约时才启用。详见[可靠消息](../capabilities/reliable-messaging.md)。

## Web、锁与替换

Web MVC starter 是入站 adapter。它为受支持的 jfoundry 异常输出共享 RFC 9457 契约，并与 Spring MVC 自身的 HTTP 错误处理协作；领域和应用代码不应直接选择 HTTP 状态码。

Redisson 锁是可选项。仅当用例需要跨实例协调，且数据库约束、幂等或本地同步不足以满足该需求时使用。

自动配置的默认实现都可以替换。应用 Bean 对 `TransactionRunner`、`PersistenceFailureTranslator`、`AggregatePersistenceContext`、`MessageSender`、`PayloadSerializer`、Outbox/Inbox store 及其专用策略具有优先权。

## 验证

运行时本地的集成 profile 会验证 Spring Boot 装配、starter 依赖边界、自动配置，以及通过
Testcontainers 运行的 middleware 路径，包括 MySQL、PostgreSQL、Kafka 和 RabbitMQ：

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pit verify
```

同一模块还包含最小 AOT consumer。在 GraalVM Native Image 环境中，`native` profile 会构建它，CI 随后启动
可执行文件，并验证 `GET /jfoundry/native/ready` 返回 `ready`：

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pnative package
```
