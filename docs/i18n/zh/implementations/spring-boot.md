# Spring Boot 运行时装配

Spring Boot 是运行时无关 jfoundry 核心的对等运行时集成。它通过 Spring Boot 启动器和条件化自动配置组装已选择的能力，不会让 Spring API 进入领域或应用模型；基础启动器也不代表自动启用全部能力。

## 公共前置依赖

所有外部应用都必须让 `jfoundry-dependencies` 参与依赖管理，它管理 JFoundry 核心、架构和框架无关适配器的版本。
使用 JFoundry Boot Parent 时由 Parent 自动导入，否则应用需要显式导入。它属于 `<dependencyManagement>`，不是运行时
依赖。Spring Boot 运行时 BOM 只管理 Spring 平台版本，不能替代它。

## Spring Boot

仅使用 Spring Boot 的应用应将 `jfoundry-spring-boot-parent` 作为唯一 Maven Parent。该 Parent 继承受支持的
Spring Boot Parent，设置 Java 25，并已经按正确顺序导入 `jfoundry-spring-boot-dependencies` 与
`jfoundry-dependencies`；使用该 Parent 时不需要再次手动导入这两个 BOM。

```xml
<parent>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-spring-boot-parent</artifactId>
    <version>${jfoundry.version}</version>
</parent>
```

`jfoundry-spring-boot-starter` 只是所有 Spring Boot 能力启动器共用的最小基础层，不代表事务、持久化、消息或
Outbox 等业务能力。通常直接选择所需的能力启动器；它会按需传递该基础层，不要把基础 starter 当作事务启动器。

## Spring Cloud

需要 Spring Cloud 或 Spring Cloud Alibaba 的应用不能使用上面的 JFoundry Boot Parent。应用应使用与受支持
Spring Cloud 版本兼容的自有或标准 Maven Parent，先导入 `jfoundry-spring-cloud-dependencies`，再导入
`jfoundry-dependencies`：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-cloud-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Cloud BOM 管理 Spring Cloud 和 Spring Cloud Alibaba；Spring Boot 由应用 Parent 或另一个显式 Boot BOM 管理。
不得同时导入 `jfoundry-spring-boot-dependencies` 与 `jfoundry-spring-cloud-dependencies`。Cloud BOM 只管理平台生态，
不会自动引入 Cloud starter，也不表示 JFoundry 为每个 Cloud 组件提供适配器。

保留其它 Maven Parent 的 Boot 应用，也应按[接入指南](../integration/getting-started.md)先导入
`jfoundry-spring-boot-dependencies`，再导入 `jfoundry-dependencies`，并自行管理 Java 与 Spring Boot 版本。

其它能力都需要显式选择，从而使应用的数据源、投递、调度和分布式锁决策保持清晰。

## 能力组合

| 需求 | 添加 | 边界 |
|---|---|---|
| 本地应用事务 | `jfoundry-transaction-spring-boot-starter` | 提供 Spring `TransactionRunner` 集成；事务管理器由 Spring Boot 或应用提供。 |
| 本地领域事件监听 | `jfoundry-domain-event-spring-boot-starter` | 通过 Spring 应用事件发布领域事件；不是 Outbox 或消息代理。 |
| MyBatis-Plus 聚合持久化 | `jfoundry-persistence-mybatis-plus-spring-boot-starter` | 仅业务聚合持久化，不含 Outbox/Inbox 存储。 |
| JPA 聚合持久化 | `jfoundry-persistence-jpa-spring-boot-starter` | 每个聚合一个受管实体图，不含 Outbox/Inbox 存储。 |
| RFC 9457 Web MVC 错误响应 | `jfoundry-webmvc-spring-boot-starter` | 仅 HTTP 入站适配。 |
| 出站 `RestClient` 支持与可配置 HTTP 日志 | `jfoundry-web-spring-boot-starter` | 应用于 Spring Boot 管理的 `RestClient.Builder`；手工 builder 使用 Java API。 |
| JSON 序列化契约 | `jfoundry-messaging-spring-boot-starter` | 提供 Spring 消息集成和默认 Jackson `PayloadSerializer`，不提供真实发送器。 |
| Kafka、RabbitMQ 或 RocketMQ 投递 | 对应 `jfoundry-messaging-*-spring-boot-starter` | 显式选择具体消息代理传输方式。 |
| Outbox 能力 | `jfoundry-outbox-spring-boot-starter` | 提供记录、外部化、恢复、清理和内置定时派发触发。手工组合时直接添加；内置存储与 JobRunr 启动器会传递引入它。 |
| Outbox 存储 | `jfoundry-outbox-jpa-spring-boot-starter`、`jfoundry-outbox-mybatis-plus-spring-boot-starter` 或应用 `OutboxMessageStore` | 只持久化 Outbox 记录；内置存储启动器也会引入 Outbox 能力，迁移仍由应用负责。 |
| Inbox 运行时与存储 | `jfoundry-inbox-spring-boot-starter` 加一个 `jfoundry-inbox-*-spring-boot-starter` | 消费端幂等；迁移由应用负责。 |
| Outbox 派发触发方式 | 内置定时模式、可选的 `jfoundry-outbox-jobrunr-spring-boot-starter` 或应用派发器 | JobRunr 会替换内置触发方式并传递引入 Outbox 能力；任何选项仍需要 Outbox 存储和真实发送器。 |
| Redisson 分布式锁 | `jfoundry-lock-redisson-spring-boot-starter` | 仅可选的跨实例锁能力。 |

完整启动器清单、配置项、条件和 Bean 优先级见 [Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)。

## 事务与领域事件

优先使用运行时无关的 `TransactionRunner` 表达可移植的应用事务边界。Spring 将该契约映射到其事务基础设施并支持六种 jfoundry 传播模式。当应用明确选择 Spring 语义时，也可以使用 Spring `@Transactional`；不要在同一用例上叠加彼此独立的事务边界，除非已明确其所有权规则。详见[应用事务](../capabilities/application-transactions.md)。

事件启动器会启用应用服务领域事件分发，并通过 Spring `ApplicationEventPublisher` 发布已分发的事件。普通监听器在进程内观察发布；`@TransactionalEventListener` 可选择 `AFTER_COMMIT` 等事务阶段。这与 Outbox 路径不同。应用服务调用失败时，待分发的聚合事件不会被发布。聚合行为仍使用 `recordEvent(...)` 显式记录每个领域事实。持久化在活动 Spring 事务中注册聚合时，运行时会在该事务的 `beforeCommit` 阶段派发事件，使聚合变更与任意 Outbox 记录原子提交，而 Spring 事件适配器仍只会在提交后发布。没有活动事务时，运行时才回退到最外层 `@ApplicationService` 成功完成时派发。该自动路径中的应用业务代码不调用 `drainEvents()`。

## 持久化

持久化启动器的名称表达它们装配的能力，而不只是引入的 ORM。`jfoundry-persistence-mybatis-plus-spring-boot-starter` 装配业务聚合的 MyBatis-Plus 持久化能力，并为实现 `AuditStampHolder` 的数据对象装配默认技术审计处理器；JPA 对应启动器则装配 JPA 聚合适配器、Spring 事务绑定的持久化上下文和 Spring Boot JPA 运行时。共享持久化自动配置提供 UTC 审计时间与空的操作者提供器；应用通常由安全集成提供 `AuditActorProvider`。

二者都与可靠消息存储明确分离。只有当用例需要可靠外部发布或消费端幂等时，才选择对应 Outbox 或 Inbox 启动器；业务数据与可靠消息存储通常使用相同的持久化技术。聚合映射、乐观锁和仓储形态见 [MyBatis-Plus](mybatis-plus.md) 与 [JPA](jpa.md) 实现指南。

## 可靠消息

Outbox 装配包含四项独立决策：能力、存储、派发触发方式和消息传输。模块名中相同的 `outbox` 前缀只表示适配器服务于该能力，不表示 JPA、MyBatis-Plus 或 JobRunr 各自构成完整方案。JFoundry 不会创建数据库表，也不会虚构消息目的地；将所选 SQL 模板复制到应用自己的迁移流程中。

`jfoundry-messaging-spring-boot-starter` 不会注册回退 `MessageSender`。启用投递前，必须添加一个消息代理专用启动器或提供应用 `MessageSender`，否则不存在生产投递路径。自动 Outbox 事件记录默认关闭，通过 `jfoundry.domain.event.dispatch.outbox.enabled=true` 启用。它只写入标注 `@Externalized` 的领域事件或被 `DomainEventExternalizer` 选中的事件，绝不会从持久化变更推断消息。直接选择消息代理见[消息传输](../capabilities/message-delivery.md)，Outbox 与 Inbox 语义见[可靠消息](../capabilities/reliable-messaging.md)。

## Web、锁与替换

Web MVC 启动器是入端适配器。它为受支持的 jfoundry 异常、应用提供的 `ProblemMapper` 映射以及 `ProblemCatalog` 支持的 Spring MVC HTTP 错误输出共享 RFC 9457 契约；领域和应用代码不应直接选择 HTTP 状态码。其他 Spring MVC 错误保留 Spring 原有的状态码和问题响应。自动配置先于 Spring Boot 的 Web MVC 问题详情配置执行，因此启用 `spring.mvc.problemdetails.enabled` 不会引入并行的处理器。它不会配置认证或授权。拥有这些语义的安全适配器可使用 `ProblemDetailRenderer.render(...)` 渲染自己的 `401` 或 `403` 描述符。共享契约与能力选择入口见[Web](../capabilities/web.md)。

对于目录支持的 Spring MVC 客户端错误，JFoundry 保留目录中稳定的 `type` 和 `status`，同时使用 Spring
Framework 针对具体异常生成的 `title` 和 `detail`，包括 `MessageSource` 本地化结果。例如，缺少请求参数时会
指出参数名，请求方法不受支持时会指出该方法，请求体无法读取时会说明读取失败。Spring 未提供具体问题响应时，
继续使用目录文案作为回退。服务端故障仍只使用经过审查的目录文案，不会暴露异常消息、cause 或其他诊断信息。
类型转换失败时会在可用的情况下指出对应请求属性，但不会回显被拒绝的值。

Spring MVC 请求入参校验失败时使用独立的 `urn:jfoundry:problem:request-validation` type。它的
`errors` 扩展遵循 RFC 9457 的 validation error 示例：每一项都包含面向调用方的 `detail`；只有能够确认属于
JSON body 字段的错误才会包含以 JSON Pointer URI fragment 表示的 `pointer`。query、path、header、cookie、
matrix、model attribute 和 multipart 错误只包含 `detail`。对象级约束与跨参数约束没有可靠的 JSON 位置，
同样只包含 `detail`：

```json
{
  "type": "urn:jfoundry:problem:request-validation",
  "title": "Request validation failed",
  "status": 400,
  "detail": "The request failed validation. See 'errors' for details.",
  "errors": [
    {
      "detail": "不能为空",
      "pointer": "#/services"
    }
  ]
}
```

响应不会包含被拒绝的值，因为请求字段可能携带凭证、令牌或体积较大的数据。Spring MVC 从
`MethodArgumentNotValidException` 与 `HandlerMethodValidationException` 生成该共享契约；返回值校验仍作为
服务端失败处理。Quarkus 与 Helidon 则从各自运行时的请求校验异常生成相同的外部表示，具体见对应的实现指南。

`jfoundry-web-spring` 为出站 `RestClient` 调用提供显式 Spring Web 集成。只对拥有该集成的 builder 使用
`RestClientSupport.configure(builder)`，并通过 `RestClientSupport.execute(...)` 执行选定调用。非成功响应会转换为
只包含状态码的 `HttpResponseException`；传输和响应解码失败会转换为带有安全失败类别的
`HttpRequestException`。`HttpLoggingLevel` 从 `org.jfoundry.http` 导入，Spring 日志支持从
`org.jfoundry.http.spring` 导入，执行链拦截器从
`org.jfoundry.http.spring.client` 导入，`RestClient` API 从 `org.jfoundry.web.spring.client` 导入。原来的
`org.jfoundry.web.spring` 位置不提供转发别名。

Spring Boot 应用可以使用 `jfoundry-web-spring-boot-starter`，并通过
`jfoundry.web.rest-client.logging-level` 选择 `NONE`、`BASIC`、`HEADERS` 或 `FULL`，默认值为 `NONE`。
直接通过 `RestClient.builder()` 创建 builder 时，仍需使用
`RestClientSupport.configure(builder, HttpLoggingLevel)`。出站 `duration` 字段以 `duration=30ms` 形式输出，并使用
单调时钟从进入执行链开始计时，到响应 header 到达或执行失败时结束；响应 body 消费与解码不在该边界内。

`jfoundry-webmvc-spring-boot-starter` 会为 Servlet 应用自动配置 `HttpLoggingFilter`。
`jfoundry.web.mvc.logging-level` 默认值为 `NONE`，因此升级不会静默增加访问日志量。启用后的注册覆盖
`REQUEST`、`ASYNC` 与 `ERROR`，支持异步处理，默认顺序为 `Ordered.HIGHEST_PRECEDENCE + 20`，位于 Spring
Security 常规注册之前。应用可以提供自己的 `HttpLoggingFilter` 或
`FilterRegistrationBean<HttpLoggingFilter>`，以适配转发、追踪或安全拓扑所需的其他顺序。

入站时长在同步链完成，或异步请求进入 complete、error、timeout 终态时结束。`FULL` 使用 tee 包装器立即转发
请求与响应字节，并最多保留 8 KiB；该时长不表示客户端何时收到流式响应。两个方向都以 `INFO` 分类输出 request、
header、body 与 response 事件，始终移除 URI query，并脱敏敏感 header 与嵌套 JSON 字段；不安全的 body
表示会被省略。这些日志用于补充而不是替代
Micrometer 指标/追踪与应用拥有的业务审计事件。

Redisson 锁是可选项。仅当用例需要跨实例协调，且数据库约束、幂等或本地同步不足以满足该需求时使用。

自动配置的默认实现都可以替换。应用 Bean 对 `TransactionRunner`、`PersistenceFailureTranslator`、`AggregatePersistenceContext`、`MessageSender`、`PayloadSerializer`、Outbox/Inbox 存储及其专用策略具有优先权。

## 验证

运行时本地的集成配置档会验证 Spring Boot 装配、启动器依赖边界、自动配置，以及通过
Testcontainers 运行的中间件路径，包括 MySQL、PostgreSQL、Kafka 和 RabbitMQ：

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pit verify
```

同一模块还包含面向受支持 Spring Boot 版本的最小 AOT 使用方。在 GraalVM 原生镜像环境中，`native` 配置档会构建它，
CI 随后启动可执行文件，并验证 `GET /jfoundry/native/ready` 返回 `ready`。这构成基础 Spring Boot 启动器与
Web MVC 装配的原生镜像支持声明；它不认证可选的持久化、消息代理、锁或调度器适配器。各项能力必须先具备
独立的原生镜像集成验证，才能声明受支持：

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pnative package
```

`native-mybatis-plus` 配置档单独认证 Spring Boot MyBatis-Plus 持久化 starter 的 GraalVM
原生镜像支持。测试在 JVM 进程中启动 PostgreSQL，启动生成的原生可执行程序，并验证
业务自定义 `AuditStampHolder` 的插入、重新加载、更新和再次加载，以及自动填充的 `createdAt`、
`createdBy`、`lastModifiedAt` 和 `lastModifiedBy`。该声明只适用于受支持的 Spring Boot、MyBatis-Plus 版本以及
PostgreSQL；不认证 JPA、消息代理、Redisson 或 JobRunr。精确测试版本记录在
[兼容矩阵](../../../release/compatibility.md)。此外，它还会
通过追加、分页认领、幂等认领和处理完成状态迁移验证内置的 MyBatis-Plus Outbox 与 Inbox 存储：

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests \
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
