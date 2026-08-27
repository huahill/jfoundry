# 框架边界设计

本文面向维护者和贡献者，定义框架代码的归属，并说明 jfoundry 如何保持 core 不依赖具体运行时框架。

## 核心决策

jfoundry core 模块不得依赖 Spring、Spring Boot、Helidon、Quarkus、Micronaut、CDI 或 Jakarta EE 运行时集成 API。jMolecules 和 `slf4j-api` 等稳定且低侵入的库只有在表达契约时才可进入 core。

`jfoundry-core` 是运行时无关框架模块的目录分组，包含领域、架构、应用和基础设施模块；它不改变这些模块内部的 Onion 依赖方向。`jfoundry-runtime` 聚合外层运行时适配器。`jfoundry-jakarta` 包含由多个 Jakarta 运行时复用的可移植 JAX-RS 与 JTA 实现，但不负责 CDI 注册或容器生命周期。Spring 使用 `runtime/`、`autoconfigure/` 和 `starters/`，Quarkus 使用 `runtime/` 和 `deployment/`；每种运行时均直接包含一个 `jfoundry-<runtime>-integration-tests` 模块。

## 模块职责

| 区域 | 模块 |
|------|------|
| 领域与架构 | `jfoundry-domain`、`jfoundry-architecture`、`jfoundry-hexagonal`、`jfoundry-onion`、`jfoundry-cqrs` |
| 应用契约 | `jfoundry-application-core`、`jfoundry-transaction-core`、`jfoundry-domain-event-core`、`jfoundry-domain-event-externalization-core`、`jfoundry-messaging-core`、`jfoundry-outbox-core`、`jfoundry-inbox-core` |
| 运行时无关适配器 | `jfoundry-persistence-core`、`jfoundry-persistence-mybatis-plus`、`jfoundry-persistence-jpa`、`jfoundry-messaging-jackson`、Outbox/Inbox MyBatis-Plus 与 JPA 存储、JobRunr 派发适配器 |
| 共享 Jakarta 适配器 | `jfoundry-http-jaxrs`、`jfoundry-web-jaxrs`、`jfoundry-restclient-jaxrs`、`jfoundry-transaction-jta`、`jfoundry-domain-event-jta` |
| Spring 运行时集成 | `jfoundry-runtime/jfoundry-spring/runtime/*` |
| Spring Boot 集成 | `jfoundry-runtime/jfoundry-spring/autoconfigure/*`、`jfoundry-runtime/jfoundry-spring/starters/*` |
| Spring 集成测试 | `jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests` |
| Quarkus 运行时集成 | `jfoundry-runtime/jfoundry-quarkus/runtime/*`、`deployment/*` |
| Quarkus 集成测试 | `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests` |
| Helidon MP 运行时集成 | `jfoundry-runtime/jfoundry-helidon` 下直接按能力划分的 `jfoundry-*-helidon` 模块 |
| Helidon 集成测试 | `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests` |

## 运行时能力命名

入站 HTTP 服务端适配器使用 `web`，出站 HTTP 客户端适配器使用 `restclient`。Quarkus extension 保留
`-runtime` 与 `-deployment` 后缀，因为两者构成真实的 Quarkus extension 配对。Helidon 没有对应的
deployment 构件，因此能力构件使用不带 `-runtime` 的 `jfoundry-<capability>-helidon`。

Spring 将共用的 HTTP 日志策略适配放在 `jfoundry-http-spring`，将入站 Spring MVC 行为放在
`jfoundry-webmvc-spring`，将出站 `RestClient` 行为放在 `jfoundry-restclient-spring`。对应的
Spring Boot 自动配置模块与启动器仍按能力划分。

两个 Jakarta 运行时都按能力划分事务、本地领域事件与聚合持久化上下文集成。Quarkus 使用相互匹配的
`jfoundry-transaction-quarkus-*`、`jfoundry-domain-event-quarkus-*` 与
`jfoundry-persistence-quarkus-*` runtime/deployment 配对；Helidon 使用没有 deployment 构件的
`jfoundry-transaction-helidon`、`jfoundry-domain-event-helidon` 与 `jfoundry-persistence-helidon`。

## 放置规则

- Spring Framework 生命周期、事务同步、调度、事件发布、MVC API 和 Spring 侧客户端包装器位于 `../../../../jfoundry-runtime/jfoundry-spring/runtime`。
- Spring Boot 条件、`@ConfigurationProperties`、Bean 装配、元数据和 `AutoConfiguration.imports` 位于 `../../../../jfoundry-runtime/jfoundry-spring/autoconfigure` 下对应的能力模块。
- Spring 中间件和 Testcontainers 验证位于 `jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests`。
- 多个运行时共用的可移植 JAX-RS 过滤与正文日志、Jakarta Transactions 执行和 JTA 领域事件协调位于
  `jfoundry-runtime/jfoundry-jakarta`。这些模块不注册 CDI Bean 或 provider；具体运行时仍负责发现、生命周期、
  配置项、日志桥、构建期处理与原生镜像集成。
- Quarkus 运行时和原生镜像验证位于 `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests`；未来的 Quarkus 中间件或 Testcontainers 验证也位于该模块。
- Helidon CDI 生命周期、JTA、JAX-RS、调度和 JPA 集成位于 `jfoundry-runtime/jfoundry-helidon` 下直接按能力划分的模块；当前原生镜像验证位于 `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests`，未来的 Helidon 中间件或 Testcontainers 验证也位于该模块。Helidon 没有中间 `runtime` 目录、JFoundry 部署模块或启动器层。
- 使用方应直接依赖领域、应用、架构风格和框架无关适配器模块。运行时专属启动器只是依赖入口，
  不得承载运行时行为。
- 运行时无关的数据库、序列化器和调度适配器位于 `jfoundry-core/jfoundry-infrastructure`。
- 消息代理客户端 `MessageSender` 适配器位于各自的运行时集成；应用层 `MessageSender` 与 `SendResult` 契约仍保持运行时无关。
- 运行时特定的中间件集成测试和 Testcontainers 兼容性验证位于相应运行时直接包含的集成测试模块；运行时无关测试紧邻其所验证的 core 或 infrastructure 实现。

## 依赖管理边界

`jfoundry-foundation-dependencies` 只管理运行时无关的库和测试工具。同一组件族的运行时无关坐标可以由 Foundation 管理，但其运行时特定的启动器、部署制品或原生镜像集成不得进入 Foundation。例如，Foundation 管理 MyBatis-Plus、JobRunr、Redisson 和 jMolecules 的运行时无关坐标，但不管理它们的 Spring 特定制品。

各运行时 BOM 分别拥有自己的生态：`jfoundry-spring-boot-dependencies` 管理 Spring Boot 与 Spring 特定集成坐标，`jfoundry-quarkus-dependencies` 管理 Quarkus 坐标，`jfoundry-helidon-dependencies` 管理 Helidon 坐标。运行时 BOM 彼此独立，不得导入 Foundation 或其他运行时 BOM。若官方平台 BOM 会破坏 Foundation 所管理的运行时无关组件，运行时 BOM 可以提供范围严格且已记录原因的兼容性覆盖；Helidon 的 Jackson annotations 对齐即属于此类例外。

测试依赖遵循同一边界。core 模块可以使用运行时无关的 JUnit、AssertJ、Mockito、H2 或持久化框架原生测试支持。凡是启动 Spring、Quarkus 或 Helidon 的测试，都必须位于对应的直接运行时集成测试模块，并在该模块中声明相应运行时测试栈。

Jakarta 规范本身不等同于应用运行时。当某个可移植规范 API 能表达基础设施适配器的技术契约时，运行时无关的
基础设施适配器可以按最小范围依赖该 API；例如，`jfoundry-web` 通过可选的 Jakarta Validation API 转换
`ConstraintViolation`。这一规则不适用于领域层和应用层。由多个运行时复用、面向容器的可移植实现位于
`jfoundry-jakarta`，而不是 core；CDI 生命周期、运行时注册、provider 与运行时异常分类仍位于具体运行时适配器中。

CI 在 Maven 测试前运行 `scripts/verify-dependency-boundaries.sh`。该 XML 感知检查器扫描全部 reactor POM，包括测试依赖与依赖管理，并拒绝跨运行时坐标、core 中的运行时依赖以及 Foundation 中的运行时特定坐标。夹具测试和工作流自检会确保该门禁被删除或弱化时 CI 立即失败。

## Java 空值契约

领域层和应用层包使用 JSpecify `@NullMarked`，默认把引用类型声明为非空。公共 API 中确实支持
`null` 的位置使用 `@Nullable`，例如 `AggregateRepository.findById` 查询不到聚合、可选的消息路由键，
以及 Outbox/Inbox 的可选状态。可变的 `InboxMessage` 与 `OutboxMessage` 存储载体在类边界保留
`@NullUnmarked`：它们通过无参构造和映射器分阶段填充，在这一过程中对象会暂时处于不完整状态；其中稳定的
可空属性仍显式标注。

这些注解只为 Java 静态分析提供元数据，不执行运行时校验，也不替代构造器检查、
`Objects.requireNonNull`、领域不变式或 HTTP/容器边界的 Jakarta Validation。新增领域层和应用层包应使用
`@NullMarked`；只有当 `null` 确实属于受支持契约时才使用 `@Nullable`；`@NullUnmarked` 仅限于暂时无法
表达可靠静态契约的特定生命周期迁移边界。

## 可靠消息边界

`jfoundry-outbox-core` 拥有消息模型、存储契约、派发服务、重试/退避契约和状态机。

`jfoundry-outbox-spring` 拥有 Spring 运行时集成，例如事务同步、scheduled dispatch 和 Spring 运行时中的领域事件记录。

`jfoundry-outbox-spring-boot-autoconfigure` 拥有 Outbox 配置项、条件和 Bean 装配。`OutboxDispatcherProperties` 及关联属性位于这里，因为属性绑定属于 Boot 职责。

`jfoundry-outbox-jobrunr` 是纯 JobRunr 派发适配器；它的 Spring Boot 自动配置也属于 `jfoundry-outbox-spring-boot-autoconfigure`。

`jfoundry-outbox-jpa` 和 `jfoundry-inbox-jpa` 是运行时无关的 Jakarta Persistence 适配器。它们实现 Outbox 和 Inbox 存储 SPI，不要求 Spring 或 Spring Boot。它们的 Spring Boot 启动器，即 `jfoundry-outbox-jpa-spring-boot-starter` 和 `jfoundry-inbox-jpa-spring-boot-starter`，是显式能力选择；通用 `jfoundry-persistence-jpa-spring-boot-starter` 只提供业务 JPA 运行时装配，不会引入任一存储。

实现机制和数据库限制属于 [JPA 实现指南](../implementations/jpa.md)。能力状态模型和 SQL 模板策略属于[可靠消息](../capabilities/reliable-messaging.md)。

## 合并验证

所有变更必须通过 Pull Request 进入 `main`，并使用 GitHub 的 `Rebase and merge` 策略；不允许直接推送。始终执行的 `Merge gate` 是必需状态检查。仅文档变更只有在文档验证成功时才可通过；任何代码变更都要求现有全部 CI 任务成功，包括运行时中间件和原生镜像验证。运行时任务被跳过、取消或失败都不能满足门禁要求。

贡献者应在推送分支前运行与所改能力对应的本地 CI 对齐阶段。本地验证可以缩短反馈时间，但不能替代服务端门禁。

## 验收标准

- 领域层和应用层模块对 Spring、Spring Boot、Helidon、Quarkus、Micronaut、CDI、Jakarta API、消息代理客户端
  和持久化框架细节没有编译期或仅提供依赖。基础设施适配器可以使用范围严格的可移植 Jakarta 规范 API，但不得
  依赖容器集成 API。
- 适配器模块不得直接注册 Spring Boot 自动配置。
- 启动器保持为轻量依赖选择。
- 未来运行时集成可以复用核心 SPI 和运行时无关适配器，而不依赖 Spring Boot。
- 基于 Jakarta 的运行时复用 `jfoundry-jakarta` 实现，同时在各自模块内保留运行时注册、生命周期、配置、日志、
  构建期与原生镜像行为。
- Foundation 只管理运行时无关坐标；各运行时 BOM 管理与自身匹配的生态。
- core 测试不得通过宽泛的启动器依赖间接获得运行时测试框架。
- 领域层和应用层包使用 JSpecify 声明 Java 空值默认规则，并显式标注受支持的可空 API 位置。
