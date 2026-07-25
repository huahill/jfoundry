# Helidon MP 运行时集成

`jfoundry-helidon` 将 JFoundry 的运行时无关契约与 Helidon MP 4.5.1 组合。它是可移植的
CDI/Jakarta 运行时集成，不是 Spring Boot starter，也不是 Quarkus extension。Helidon、CDI、JTA、
JAX-RS 和 Hibernate API 都应停留在 domain 和 application 代码之外。

## 依赖组合

先导入核心 JFoundry BOM，再导入同一发布线的 Helidon BOM。Helidon BOM 只管理所选 Helidon
平台生态版本，不管理 JFoundry 模块版本：

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
            <artifactId>jfoundry-helidon-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

再只选择应用真正需要的能力：

| 能力 | JFoundry 构件 | 应用提供的 Helidon 能力 |
|---|---|---|
| CDI 事务与本地领域事件 | `jfoundry-helidon-runtime` | Helidon MP server 与 JTA CDI 集成 |
| JPA 聚合持久化 | `jfoundry-persistence-jpa-helidon-runtime` | CDI JPA/Hibernate 集成、数据源与 persistence unit |
| RFC 9457 JAX-RS 响应 | `jfoundry-web-helidon-runtime` | Helidon MP server |
| Outbox 调度、派发与自动事件外部化 | `jfoundry-outbox-helidon-runtime` | `OutboxMessageStore` 与真实 `MessageSender` |
| JPA Outbox store | `jfoundry-outbox-jpa-helidon-runtime` | JPA 能力与应用迁移 |
| JPA Inbox store | `jfoundry-inbox-jpa-helidon-runtime` | JPA 能力与应用迁移 |

通用 runtime 不会隐式引入 JPA、Outbox、Inbox、数据库或 broker client。

## 事务与领域事件

`jfoundry-helidon-runtime` 通过可移植 CDI 暴露 `TransactionRunner`，并将六种
`TransactionPropagation` 映射到 Jakarta Transactions。它支持由自身创建事务的超时；Jakarta
Transactions 没有可移植的事务名称和只读语义，因此会拒绝这两类选项，而不是静默忽略。

runtime 同时向标注 JFoundry `@ApplicationService` 的 CDI Bean 加入 interceptor。它收集通过
`DomainEventContext` 注册的领域事件，在最外层应用服务成功完成后发布；该调用失败时则丢弃事件。
此边界仅支持同步调用，不支持 reactive 返回类型。

## JPA、Outbox 与 Inbox

JPA 聚合能力提供事务绑定的聚合持久化上下文，并将已识别的 Hibernate 连接和查询超时失败转换为
`ExternalAccessException`。`EntityManager` 由 Helidon 应用提供。

JPA Outbox 与 Inbox 能力复用运行时无关的 JPA store，不会创建 SQL 表。应用必须将发布的 Outbox 和
Inbox SQL 模板复制到自己的迁移流程。Inbox claim strategy 支持 PostgreSQL 与 MySQL；其它数据库需要
由应用提供 `JpaInboxClaimStrategy` Bean。

`jfoundry-outbox-helidon-runtime` 提供按需启用的调度。只有在提供 store 和 broker sender 后才启用
定时派发：

```properties
jfoundry.outbox.dispatcher.enabled=true
```

dispatcher 属性沿用运行时无关的 Outbox 行为：`interval` 默认 `5s`、`batch-size` 默认 `50`、
`max-retries` 默认 `5`、`backoff-base` 默认 `1s`、`backoff-max` 默认 `5m`。

当配置 `jfoundry.domain.event.dispatch.outbox.enabled=true` 时，它还会将标记 `@Externalized` 的领域事件
写入当前事务。该装配以 CDI alternative（优先级 `1`）提供 Jackson 序列化、路由 resolver、Outbox template
和 recorder。若要在可移植 Helidon 应用中替换这些默认实现，应用实现必须声明为已启用的 CDI `@Alternative`，且
`@Priority` 高于 `1`；普通 CDI Bean 不能覆盖已启用的 alternative。

## Web Problem

`jfoundry-web-helidon-runtime` 会将 JFoundry application 与 domain 异常映射为 RFC 9457
`application/problem+json` JAX-RS 响应。未知异常和不相关的 HTTP 失败仍交给 Helidon 原有处理；该
adapter 不替代应用通用的 JAX-RS 错误策略。

## PostgreSQL/JTA 中间件验证

运行时本地的 JVM 集成 profile 会通过 Testcontainers 启动 PostgreSQL，并验证真实的 JTA
`TransactionRunner` 回调与 JPA `EntityManager`。Helidon 的 CDI JPA 集成使用标准
`META-INF/persistence.xml` persistence unit 描述符，并通过 CDI 解析具名 JTA datasource；验证覆盖的
正是这一装配模型。该 profile 保持显式启用，因此普通模块测试不需要 Docker：

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-helidon/jfoundry-helidon-integration-tests \
  -am -Pjvm-integration verify
```

## Native Image 状态

Helidon consumer 已用 GraalVM Native Image 构建，并验证 CDI 发现、应用启动和 Problem Details HTTP
响应。使用 GraalVM 25、Maven 3.9 与仓库 Native Image profile：

```bash
GRAALVM_HOME=/path/to/graalvm-25 \
JAVA_HOME="$GRAALVM_HOME" PATH="$GRAALVM_HOME/bin:$PATH" \
mvn -pl jfoundry-runtime-integrations/jfoundry-helidon/jfoundry-helidon-integration-tests \
  -am -Pnative-image package
```

Helidon MP 4.5.1 将 Narayana JTA 的 Native Image 支持标为实验性。在 macOS ARM64 上使用 GraalVM Community
25.0.2 时，启用 JPA 的 consumer 会在镜像生成阶段失败：
`JpaExtension.processPersistenceXmls` 会使 `org.xml.sax.helpers.LocatorImpl` 进入 image heap。
仅包含 Native CDI/Web 的 consumer 可以启动并提供 Problem Details，但执行 `TransactionRunner` 时仍会因
Helidon CDI transaction-manager delegate 未在镜像中初始化而失败。JVM JTA 仍受支持。可复现环境、JVM
对照结果与 Native 失败栈已记录在 [Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015)。
JFoundry 不会复制或替换 Narayana 来掩盖该上游限制，因此在 Helidon 提供可用的受支持路径前，Native JTA 与
JPA 都不能作为验收结论。

## 延后集成

当前没有 Helidon Kafka 或 RabbitMQ `MessageSender` adapter、Redisson 分布式锁或 JobRunr。不要在
Helidon 应用中复用 Spring 或 Quarkus runtime adapter。只有在所选 Helidon 版本中验证 client 生命周期和
投递语义后，才应添加应用自有 adapter。
