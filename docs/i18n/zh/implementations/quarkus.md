# Quarkus 运行时集成

`jfoundry-quarkus-runtime` 是一个 Quarkus 扩展，将运行时无关的 `TransactionRunner` 暴露为 CDI Bean。
它使 Quarkus、CDI、Jakarta Transactions 与 GraalVM 类型始终位于 domain、application 和
infrastructure 模块之外。

## 依赖配置

先导入核心 JFoundry BOM，再导入同一发布线的 Quarkus BOM，最后添加运行时扩展。
`jfoundry-quarkus-dependencies` 只管理 Quarkus 平台生态版本，不管理 JFoundry 模块版本。Quarkus 会通过
运行时扩展描述符发现部署构件；应用不应直接添加部署构件。

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
            <artifactId>jfoundry-quarkus-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-quarkus-runtime</artifactId>
    </dependency>
</dependencies>
```

该扩展在运行时引入 Quarkus Arc 与 Narayana JTA，并注册一个 application scope 的
`QuarkusTransactionRunner`，应用可通过运行时无关的 `TransactionRunner` 契约注入它。

## Spring Boot 与 Quarkus 的依赖组合

Spring Boot 启动器用于选择依赖集合，并依赖 Boot 自动配置。Quarkus 应用显式组合扩展；Quarkus
会从每个运行时构件自动发现其匹配的部署构件。

| Spring Boot 能力 | Quarkus 依赖组合 |
|---|---|
| `jfoundry-spring-boot-starter` | `jfoundry-quarkus-runtime` |
| `jfoundry-domain-event-spring-boot-starter` | `jfoundry-quarkus-runtime` |
| `jfoundry-persistence-jpa-spring-boot-starter` | `jfoundry-quarkus-runtime`、`jfoundry-persistence-jpa`、`jfoundry-persistence-jpa-quarkus-runtime`、`quarkus-hibernate-orm` 及所选 Quarkus JDBC extension |
| `jfoundry-outbox-jpa-spring-boot-starter` | 上述 JPA 组合，加上 `jfoundry-outbox-jpa-quarkus-runtime`；需要派发时再加 `jfoundry-outbox-quarkus-runtime` |
| `jfoundry-inbox-jpa-spring-boot-starter` | 上述 JPA 组合，加上 `jfoundry-inbox-jpa-quarkus-runtime` |
| Kafka 或 RabbitMQ messaging starter | `jfoundry-messaging-kafka-quarkus-runtime` 或 `jfoundry-messaging-rabbitmq-quarkus-runtime` |
| `jfoundry-webmvc-spring-boot-starter` | `jfoundry-web-quarkus-runtime` |

## 已支持范围

Quarkus 不是 Spring starter 的翻译层。当前显式依赖组合覆盖 CDI/JTA 事务、本地 CDI 领域事件投递、JPA
聚合持久化、JPA Outbox 和 Inbox 存储、Outbox 派发与维护、Kafka 和 RabbitMQ 投递，以及 REST Problem Details。
通用的 `jfoundry-web-quarkus-runtime` 扩展当前提供 Problem Details 适配器，并作为后续 Quarkus Web
入站集成的归属；它不会将 Web 语义移入核心。

当前并不支持 MyBatis-Plus 聚合持久化、RocketMQ 投递、Redisson 分布式锁或 JobRunr 的 Quarkus 组合。不要以
运行时无关适配器或 Spring 启动器替代；只有当项目自行拥有该集成时，才选择自定义应用适配器。

## 事务语义

适配器将全部六种 `TransactionPropagation` 映射为 Jakarta Transactions 语义：

| jfoundry propagation | Quarkus/Jakarta 行为 |
|----------------------|----------------------|
| `REQUIRED` | 加入已有事务，或新建事务。 |
| `REQUIRES_NEW` | 挂起已有事务，新建事务，完成后恢复已有事务。 |
| `SUPPORTS` | 加入已有事务；没有事务时以非事务方式运行。 |
| `MANDATORY` | 必须存在活动事务。 |
| `NOT_SUPPORTED` | 挂起已有事务，并以非事务方式运行。 |
| `NEVER` | 仅在不存在活动事务时运行。 |

回调异常会回滚由适配器创建的事务。适配器加入已有事务时，回调异常会将该事务标记为 rollback-only，且
保留原始异常。

`TransactionOptions.timeout` 会映射为适配器创建事务所使用的 Jakarta 事务超时，并在结束后恢复默认值。
Jakarta Transactions 没有可移植的事务名称或只读事务设置，因此此适配器会拒绝
`TransactionOptions.name` 与 `TransactionOptions.readOnly`，而不会静默忽略它们。

## 领域事件分发

基础运行时扩展还提供应用服务的事件边界。对于所有标注运行时无关 `@ApplicationService` 的 CDI Bean，
Quarkus 会在增强阶段加入仅限运行时的拦截器绑定。最外层调用成功后，拦截器会
从通过 `DomainEventContext` 注册的聚合中提取事件，并交给每个 CDI `DomainEventDispatcher`。
嵌套应用服务调用共享同一个作用域，因此只会在最外层边界分发一次；若异常从该边界逸出，待分发事件会被丢弃。

```java
@ApplicationScoped
@ApplicationService
class ConfirmOrder {

    private final DomainEventContext domainEventContext;

    ConfirmOrder(DomainEventContext domainEventContext) {
        this.domainEventContext = domainEventContext;
    }

    void handle(Order order) {
        order.confirm();
        domainEventContext.register(order);
    }
}
```

扩展提供此边界所使用的 `DomainEventContext`。该装配只支持同步应用服务方法，会拒绝 `CompletionStage` 和
Mutiny 返回类型；它只提供进程内领域事件编排，不会引入 Outbox 存储、序列化器、消息代理客户端或自动事件外部化。

## JPA 聚合持久化

使用 `JpaAggregateRepository` 时，除 `jfoundry-persistence-jpa` 外，还需加入
`jfoundry-persistence-jpa-quarkus-runtime`、应用所选的 Quarkus Hibernate ORM 与数据源扩展。JPA 能力
会将已知的 Hibernate 连接与查询超时失败翻译为 `ExternalAccessException`；应用可替换 CDI
`PersistenceFailureTranslator`。仓储子类必须是 CDI Bean，并通过构造器接收 `EntityManager`。
jfoundry 扩展会发现实现 `AggregatePersistenceContextAware` 的 CDI Bean，并自动注入绑定到 JTA
事务的持久化上下文。应用也可以声明自己的 CDI `AggregatePersistenceContext` Bean 覆盖此默认实现。

应将 `findById(...)`、领域行为和 `modify(...)` 保持在同一个 `TransactionRunner` 回调中。Quarkus
会把注入的 `EntityManager` 与聚合持久化状态绑定到该事务，因此仓储会更新同一持久化上下文中
已加载的实体图。

```java
transactionRunner.run(() -> {
    Order order = repository.findById(orderId);
    order.confirm();
    repository.modify(order);
});
```

此装配仅覆盖业务聚合持久化。应用需要基于 JPA 的 Outbox 存储时，应显式加入下文所述的能力。

## JPA Outbox 存储

除基础运行时扩展和 Quarkus Hibernate ORM 外，加入 `jfoundry-outbox-jpa-quarkus-runtime`：

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-outbox-jpa-quarkus-runtime</artifactId>
</dependency>
```

该能力会把 `JpaOutboxMessageEntity` 注册到默认 persistence unit，并提供由
`JpaOutboxMessageStore` 支撑的默认 CDI `OutboxMessageStore`。应用声明自己的 CDI
`OutboxMessageStore` Bean 即可覆盖它。和所有 jfoundry SQL 模板一样，应用仍负责通过自己的迁移流程维护
`jfoundry_outbox_event` 表。

该能力只装配持久化。需要派发、载荷序列化或自动领域事件外部化时，请额外加入下文所述的显式 Outbox 运行时装配。

## Outbox 派发与维护

应用需要共享的 Outbox 领取、发送和状态转换运行时时，加入 `jfoundry-outbox-quarkus-runtime`：

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-outbox-quarkus-runtime</artifactId>
</dependency>
```

该扩展提供默认 CDI `OutboxDispatcher`，并使用 Quarkus Scheduler。只有配置
`jfoundry.outbox.dispatcher.enabled=true` 时才会启动定时派发。应用必须提供
`OutboxMessageStore`（例如通过 `jfoundry-outbox-jpa-quarkus-runtime`）和真实的 `MessageSender`；
派发器不会引入消息代理客户端或日志发送器。可按需配置 `jfoundry.outbox.dispatcher.interval`
（默认 `5s`）、`batch-size`（默认 `50`）、`max-retries`（默认 `5`）、`backoff-base`（默认 `1s`）和
`backoff-max`（默认 `5m`）。应用提供的 CDI `OutboxDispatcher` 优先。

消息发送始终位于数据库事务之外。每次领取和状态转换都通过 `TransactionRunner` 在独立事务中进行，
与运行时无关的 Outbox 契约保持一致。

同一扩展还提供不依赖 `MessageSender` 的 Outbox 定时维护。恢复默认关闭；配置
`jfoundry.outbox.recovery.enabled=true` 后，会以 `jfoundry.outbox.recovery.interval`（默认 `60s`）执行，
并将超过 `jfoundry.outbox.recovery.stuck-timeout`（默认 `5m`）的 `DISPATCHING` 记录重置。清理同样默认关闭；
配置 `jfoundry.outbox.cleanup.enabled=true` 后，会以 `jfoundry.outbox.cleanup.interval`（默认 `24h`）删除过期的终态记录。
默认保留 `PUBLISHED` 记录七天、`DEAD_LETTERED` 记录 30 天，并且每次每种状态最多删除 1000 条。需要不同的运维限制时，
可在 `jfoundry.outbox.cleanup` 下配置 `published-retention-days`、`dead-lettered-retention-days` 和 `batch-size`。

恢复和每种终态记录清理都使用独立的 `REQUIRES_NEW` 事务边界。消息代理适配器和启动器仍是显式能力。

## Kafka 消息投递

加入 `jfoundry-messaging-kafka-quarkus-runtime`，即可提供默认的 Quarkus Kafka `MessageSender`
实现：

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-messaging-kafka-quarkus-runtime</artifactId>
</dependency>
```

该扩展会引入 `quarkus-messaging-kafka`，并通过固定的出站通道 `jfoundry-kafka` 发送。使用
SmallRye Kafka 连接器配置该通道：

```properties
kafka.bootstrap.servers=localhost:9092
mp.messaging.outgoing.jfoundry-kafka.connector=smallrye-kafka
mp.messaging.outgoing.jfoundry-kafka.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.jfoundry-kafka.value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

`MessageSender.send(topic, payloadKey, payload)` 会为每一条 Kafka 记录动态设置 topic 与 key，因此
`@Externalized` 和 `@AggregateRouting` 仍决定 Outbox 路由。通道名称只是基础设施配置，并非业务目的地。
适配器会等待消息代理确认，并将失败映射为 `SendResult`；投递超时由 Kafka 客户端与连接器原生属性配置。
它是 Quarkus CDI 默认 Bean，应用可以用自己的 `MessageSender` 覆盖。

## RabbitMQ 消息投递

加入 `jfoundry-messaging-rabbitmq-quarkus-runtime`，即可获得默认 RabbitMQ `MessageSender`：

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-messaging-rabbitmq-quarkus-runtime</artifactId>
</dependency>
```

适配器使用 Vert.x RabbitMQ 客户端，并只在首次发送消息时连接。`MessageSender.send(topic, payloadKey, payload)`
会将 `topic` 映射为 exchange、`payloadKey` 映射为 routing key。通过 Quarkus
`@Identifier("jfoundry-rabbitmq")` 的 `RabbitMQOptions` 生产者配置客户端；标准 Vert.x 选项覆盖主机、
凭据、TLS、恢复与连接超时。CDI 默认 Bean 可由应用自己的 `MessageSender` 覆盖。

## 自动领域事件外部化

`jfoundry-outbox-quarkus-runtime` 还提供显式的自动外部化装配。它引入 Quarkus Jackson，并提供可替换的
`PayloadSerializer`、`ExternalizationRuleResolver`、`AggregateRoutingResolver`、`OutboxTemplate` 与
`DomainEventOutboxRecorder` 默认 CDI Bean。它不会添加 Outbox 存储或消息代理客户端；应单独加入例如
`jfoundry-outbox-jpa-quarkus-runtime` 的存储能力。

自动记录默认关闭。只有领域事件本身就是稳定的集成契约时才启用：

```properties
jfoundry.domain.event.dispatch.outbox.enabled=true
```

对每个预期作为集成事件的类型添加 `@Externalized("<topic>")`。需要把聚合类型、id 或版本写入 Outbox 行时，添加
`@AggregateRouting`；在未指定路由 key 时，解析出的聚合 id 也会成为默认消息 key。没有 `@Externalized` 的事件不会被记录。
应用可以声明自己的 CDI Bean 覆盖默认序列化器或记录器。

事务边界必须包住完整的应用服务调用，包括领域事件分发。例如，可在 `@ApplicationService` 方法上使用 Jakarta
`@Transactional`，或在外层 `TransactionRunner` 回调中调用该方法。仅在应用服务内部将聚合修改包进一个
`TransactionRunner` 回调是不够的：领域事件边界会在该回调返回后才写入 Outbox。

扩展会在增强阶段为 `@Externalized` 事件类型注册 Jackson 反射元数据，因此默认序列化器可以用于
原生镜像。它不指定消息代理传输方式；需要投递时，请另行选择 `MessageSender` 适配器并启用派发器。

## JPA Inbox 存储

除基础运行时扩展和 Quarkus Hibernate ORM 外，加入 `jfoundry-inbox-jpa-quarkus-runtime`：

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-inbox-jpa-quarkus-runtime</artifactId>
</dependency>
```

该能力会把 `JpaInboxMessageEntity` 注册到默认 persistence unit，并提供默认 CDI
`JpaInboxClaimStrategy`、`InboxMessageStore` 和 `InboxTemplate` Bean。存储使用
`JpaInboxMessageStore`，模板使用运行时的 `TransactionRunner` 建立领取、处理和失败状态的事务边界。
内置领取策略会根据数据源产品选择，且只支持 PostgreSQL 与 MySQL。其他数据库应声明 CDI
`JpaInboxClaimStrategy` Bean；应用也可以声明自己的 CDI `InboxMessageStore` 或 `InboxTemplate` Bean 覆盖默认实现。

应用仍负责把 Inbox SQL 模板复制到自己的迁移流程中，并维护 `jfoundry_inbox_message` 表。该能力只装配持久化，
不提供派发器、调度器、序列化器、自动事件外部化或启动器。

## REST Problem Details

Quarkus REST 应用需要共享 RFC 9457 错误契约时，添加 `jfoundry-web-quarkus-runtime`。该扩展以更宽泛的
Quarkus Web 边界命名，Problem Details 是当前已实现的能力：

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-web-quarkus-runtime</artifactId>
</dependency>
```

该扩展会引入 Quarkus REST Jackson 支持，并为六种 JFoundry application 与 domain 异常渲染
`application/problem+json` 响应：`InvalidArgumentException`、`NotFoundException`、
`ConflictException`、`ExternalAccessException`、`DomainRuleViolationException` 和
`DomainStateException`。它还会为状态码为 `400`、`404`、`405`、`406`、`413`、`415` 和 `503`
的标准 Jakarta REST 失败渲染共享契约。

响应包含共享的 `type`、`title`、`status`、`detail` 以及 JFoundry `code` 字段。适配器会保留源
Jakarta REST 响应提供的非实体头；存在 `Allow` 时也会保留。它不会推断 Quarkus 未提供的响应头。未知异常
和其他 HTTP 状态会继续使用正常的 Quarkus 行为，而不会被转换成 JFoundry 错误。

## PostgreSQL 中间件验证

运行时本地的 JVM 集成配置档会通过 Testcontainers 启动 PostgreSQL。它验证 Quarkus 的
`TransactionRunner`、JPA Outbox 存储和数据源装配确实会在 PostgreSQL 中持久化 Outbox
记录，而不是使用内存测试数据库。该配置档保持显式启用，因此普通模块测试不需要 Docker：

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-quarkus/jfoundry-quarkus-integration-tests \
  -am -Pjvm-integration verify
```

## 原生镜像验证

仓库的 Quarkus 原生镜像 CI 任务会安装完整 Reactor，再通过 Quarkus 容器原生镜像构建独立的使用方应用。其
`@QuarkusIntegrationTest` 通过 HTTP 入口调用 `TransactionRunner`、领域事件分发、Outbox 派发、恢复和清理，针对原生可执行文件运行。

### 本地 CI 对齐验证

使用 Java 25 和 Docker 在本地运行两个 Quarkus CI 阶段。在 Linux 上，原生阶段使用与 CI 相同的容器构建；在
macOS 上，它使用本地 GraalVM，因为 Linux 容器生成的可执行文件无法在宿主机运行：

```bash
JAVA_25_HOME=/path/to/java-25 \
GRAALVM_HOME=/path/to/graalvm-25 \
bash scripts/verify-runtime-ci.sh quarkus
```

使用 `--stage middleware` 或 `--stage native` 可以只运行一个阶段。通用
`scripts/verify-ci-matrix.sh` 仍然是无需 Docker 的 Java 25 基线验证。设置两个环境变量后，使用
`bash scripts/verify-runtime-ci.sh all` 可以运行所有已支持的运行时检查。

## 当前范围

当前 Quarkus 集成覆盖 CDI 发现、应用事务、REST Problem Details、应用服务领域事件分发、JPA 聚合持久化上下文装配、可选的 JPA
Outbox 和 Inbox 存储、被明确标记事件的自动外部化、Kafka 与 RabbitMQ 消息投递，以及可选的 Outbox 派发、恢复和清理。它尚未提供
MyBatis-Plus、RocketMQ 或启动器的 Quarkus 装配；这些能力仍是后续的显式工作项。
