# Spring Boot 运行时装配

Spring Boot 是运行时无关 jfoundry 核心的对等运行时集成。它通过 Spring Boot 启动器和条件化自动配置组装已选择的能力，不会让 Spring API 进入领域或应用模型；基础启动器也不代表自动启用全部能力。

## 装配模型

在运行时装配模块导入用于 JFoundry 模块版本的 `jfoundry-dependencies`，以及用于 Spring
平台的 `jfoundry-spring-dependencies`，再添加 `jfoundry-spring-boot-starter`。基础启动器
保持轻量：它提供通用 Boot 装配和基于 Spring 的 `TransactionRunner`，但不引入持久化提供方、消息代理、Outbox、Inbox、JobRunr 或 Redisson 客户端。

Spring 运行时 BOM 管理已对齐的 Spring Boot、Spring Cloud 和 Spring Cloud Alibaba BOM。它只管理版本：应用仍需显式声明所选 Cloud 启动器；仅管理版本不表示 JFoundry 已为每项 Cloud 能力提供适配器。

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
| 本地领域事件监听 | `jfoundry-domain-event-spring-boot-starter` | 通过 Spring 应用事件发布领域事件；不是 Outbox 或消息代理。 |
| MyBatis-Plus 聚合持久化 | `jfoundry-persistence-mybatis-plus-spring-boot-starter` | 仅业务聚合持久化，不含 Outbox/Inbox 存储。 |
| JPA 聚合持久化 | `jfoundry-persistence-jpa-spring-boot-starter` | 每个聚合一个受管实体图，不含 Outbox/Inbox 存储。 |
| RFC 9457 Web MVC 错误响应 | `jfoundry-webmvc-spring-boot-starter` | 仅 HTTP 入站适配。 |
| JSON 序列化契约 | `jfoundry-messaging-spring-boot-starter` | 提供 Spring 消息集成和默认 Jackson `PayloadSerializer`，不提供真实发送器。 |
| Kafka、RabbitMQ 或 RocketMQ 投递 | 对应 `jfoundry-messaging-*-spring-boot-starter` | 显式选择具体消息代理传输方式。 |
| Outbox 运行时 | `jfoundry-outbox-spring-boot-starter` | 提供外部化和 Spring 调度集成；存储与发送器需另选。 |
| JPA 或 MyBatis-Plus Outbox 存储 | 对应 `jfoundry-outbox-*-spring-boot-starter` | 只提供数据库存储；迁移由应用负责。 |
| Inbox 运行时与存储 | `jfoundry-inbox-spring-boot-starter` 加一个 `jfoundry-inbox-*-spring-boot-starter` | 消费端幂等；迁移由应用负责。 |
| JobRunr Outbox 调度 | `jfoundry-outbox-jobrunr-spring-boot-starter` | 可选派发器，仍需要 Outbox 存储和真实发送器。 |
| Redisson 分布式锁 | `jfoundry-lock-redisson-spring-boot-starter` | 仅可选的跨实例锁能力。 |

完整启动器清单、配置项、条件和 Bean 优先级见 [Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)。

## 事务与领域事件

优先使用运行时无关的 `TransactionRunner` 表达可移植的应用事务边界。Spring 将该契约映射到其事务基础设施并支持六种 jfoundry 传播模式。当应用明确选择 Spring 语义时，也可以使用 Spring `@Transactional`；不要在同一用例上叠加彼此独立的事务边界，除非已明确其所有权规则。详见[应用事务](../capabilities/application-transactions.md)。

事件启动器会启用应用服务领域事件分发，并通过 Spring `ApplicationEventPublisher` 发布已分发的事件。普通监听器在进程内观察发布；`@TransactionalEventListener` 可选择 `AFTER_COMMIT` 等事务阶段。这与 Outbox 路径不同。应用服务调用失败时，待分发的聚合事件不会被发布。聚合行为仍使用 `recordEvent(...)` 显式记录每个领域事实；持久化注册该聚合后，运行时会在最外层 `@ApplicationService` 成功完成时提取其待派发事件。该自动路径中的应用业务代码不调用 `drainEvents()`。

## 持久化

持久化启动器的名称表达它们装配的能力，而不只是引入的 ORM。`jfoundry-persistence-mybatis-plus-spring-boot-starter` 装配业务聚合的 MyBatis-Plus 持久化能力，并为实现 `AuditStampHolder` 的数据对象装配默认技术审计处理器；JPA 对应启动器则装配 JPA 聚合适配器、Spring 事务绑定的持久化上下文和 Spring Boot JPA 运行时。共享持久化自动配置提供 UTC 审计时间与空的操作者提供器；应用通常由安全集成提供 `AuditActorProvider`。

二者都与可靠消息存储明确分离。只有当用例需要可靠外部发布或消费端幂等时，才选择对应 Outbox 或 Inbox 启动器。聚合映射、乐观锁和仓储形态见 [MyBatis-Plus](mybatis-plus.md) 与 [JPA](jpa.md) 实现指南。

## 可靠消息

Outbox 启动器按配置模式提供事务感知的记录、调度投递、恢复和清理。它不会创建数据库表，也不会虚构消息目的地；将所选 SQL 模板复制到应用自己的迁移流程中。

`jfoundry-messaging-spring-boot-starter` 不会注册回退 `MessageSender`。启用投递前，必须添加一个消息代理专用启动器或提供应用 `MessageSender`，否则不存在生产投递路径。自动 Outbox 事件记录默认关闭，通过 `jfoundry.domain.event.dispatch.outbox.enabled=true` 启用。它只写入标注 `@Externalized` 的领域事件或被 `DomainEventExternalizer` 选中的事件，绝不会从持久化变更推断消息。详见[可靠消息](../capabilities/reliable-messaging.md)。

## Web、锁与替换

Web MVC 启动器是入端适配器。它为受支持的 jfoundry 异常输出共享 RFC 9457 契约，并与 Spring MVC 自身的 HTTP 错误处理协作；领域和应用代码不应直接选择 HTTP 状态码。它不会配置认证或授权。拥有这些语义的安全适配器可使用 `ProblemDetailRenderer.render(...)` 渲染自己的 `401` 或 `403` 描述符。

Redisson 锁是可选项。仅当用例需要跨实例协调，且数据库约束、幂等或本地同步不足以满足该需求时使用。

自动配置的默认实现都可以替换。应用 Bean 对 `TransactionRunner`、`PersistenceFailureTranslator`、`AggregatePersistenceContext`、`MessageSender`、`PayloadSerializer`、Outbox/Inbox 存储及其专用策略具有优先权。

## 验证

运行时本地的集成配置档会验证 Spring Boot 装配、启动器依赖边界、自动配置，以及通过
Testcontainers 运行的中间件路径，包括 MySQL、PostgreSQL、Kafka 和 RabbitMQ：

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pit verify
```

同一模块还包含最小的 Spring Boot 4.0.7 AOT 使用方。在 GraalVM 原生镜像环境中，`native` 配置档会构建它，
CI 随后启动可执行文件，并验证 `GET /jfoundry/native/ready` 返回 `ready`。这构成基础 Spring Boot 启动器与
Web MVC 装配的原生镜像支持声明；它不认证可选的持久化、消息代理、锁或调度器适配器。各项能力必须先具备
独立的原生镜像集成验证，才能声明受支持：

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pnative package
```

`native-mybatis-plus` 配置档单独认证 Spring Boot MyBatis-Plus 持久化 starter 的 GraalVM
原生镜像支持。测试在 JVM 进程中启动 PostgreSQL，启动生成的原生可执行程序，并验证
业务自定义 `AuditStampHolder` 的插入、重新加载、更新和再次加载，以及自动填充的 `createdAt`、
`createdBy`、`lastModifiedAt` 和 `lastModifiedBy`。该声明只适用于 Spring Boot 4.0.7、
MyBatis-Plus 3.5.17 与 PostgreSQL；不认证 JPA、Outbox/Inbox 存储、消息代理、Redisson 或
JobRunr：

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pnative-mybatis-plus verify
```

`native-redisson` 配置档单独认证 Redisson 4.6.1 锁 starter 与 Redis 的组合。测试在 JVM 进程中
启动 Redis，启动生成的原生可执行程序，并验证 JFoundry `LockExecutor` 能获取和释放分布式锁。
`native-jobrunr` 配置档单独认证 JobRunr 8.7.1 与 PostgreSQL 的 Outbox 派发。它启动生成的原生
可执行程序、启用 JobRunr 后台服务器，并验证持久化的 Outbox 消息会被调度和发布。这些配置档不认证
其他 Redis、JobRunr 存储、消息代理或持久化组合。业务应用的事件载荷由应用序列化时，仍需为其类型
提供 Spring AOT binding hints。

### 本地 CI 对齐验证

使用 Java 25、Docker 和 GraalVM Native Image 在本地运行全部 Spring CI 阶段：

```bash
JAVA_25_HOME=/path/to/java-25 \
GRAALVM_HOME=/path/to/graalvm-25 \
bash scripts/verify-runtime-ci.sh spring
```

使用 `--stage middleware`、`--stage native`、`--stage native-mybatis-plus`、`--stage native-redisson`
或 `--stage native-jobrunr` 可以只运行一个阶段。通用
`scripts/verify-ci-matrix.sh` 仍然是无需 Docker 的 Java 25 基线验证。
