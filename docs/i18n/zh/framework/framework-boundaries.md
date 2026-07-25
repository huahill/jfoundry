# 框架边界设计

本文面向维护者和贡献者，定义框架代码的归属，并说明 jfoundry 如何保持 core 不依赖具体运行时框架。

## 核心决策

jfoundry core 模块不得依赖 Spring、Spring Boot、Helidon、Quarkus、Micronaut、CDI 或 Jakarta EE 运行时集成 API。jMolecules 和 `slf4j-api` 等稳定且低侵入的库只有在表达契约时才可进入 core。

`jfoundry-core` 是运行时无关框架模块的目录分组，包含 domain、architecture、application、infrastructure 和运行时无关 starter 聚合；它不改变这些模块内部的 Onion 依赖方向。`jfoundry-runtime-integrations` 聚合具体运行时集成：Spring 使用 `runtime/`、`autoconfigure/`、`starters/` 和 `integration-tests/`，Quarkus 使用 `runtime/`、`deployment/` 和 `integration-tests/`，Helidon 使用 `runtime/` 和 `integration-tests/`。

## 模块职责

| 区域 | 模块 |
|------|------|
| 领域与架构 | `jfoundry-domain`、`jfoundry-architecture`、`jfoundry-hexagonal`、`jfoundry-onion`、`jfoundry-cqrs` |
| 应用契约 | `jfoundry-application-core`、`jfoundry-transaction-core`、`jfoundry-event-core`、`jfoundry-event-externalization-core`、`jfoundry-messaging-core`、`jfoundry-outbox-core`、`jfoundry-inbox-core` |
| 运行时无关适配器 | `jfoundry-persistence-core`、`jfoundry-persistence-mybatis-plus`、`jfoundry-persistence-jpa`、`jfoundry-messaging-jackson`、Outbox/Inbox MyBatis-Plus 与 JPA store、JobRunr dispatcher adapter |
| 运行时无关 starter 组合 | Domain 与 Application starter 位于 `jfoundry-core/jfoundry-starters`；以能力命名的基础设施适配器 starter 位于 `jfoundry-core/jfoundry-starters/infrastructure` |
| Spring 运行时集成 | `jfoundry-runtime-integrations/jfoundry-spring/runtime/*` |
| Spring Boot 集成 | `jfoundry-runtime-integrations/jfoundry-spring/autoconfigure/jfoundry-spring-boot-autoconfigure`、`jfoundry-runtime-integrations/jfoundry-spring/starters/*` |
| Spring 集成测试 | `jfoundry-runtime-integrations/jfoundry-spring/integration-tests/*` |
| Quarkus 运行时集成 | `jfoundry-runtime-integrations/jfoundry-quarkus/runtime/*`、`deployment/*` |
| Quarkus 集成测试 | `jfoundry-runtime-integrations/jfoundry-quarkus/integration-tests/*` |
| Helidon MP 运行时集成 | `jfoundry-runtime-integrations/jfoundry-helidon/runtime/*` |
| Helidon 集成测试 | `jfoundry-runtime-integrations/jfoundry-helidon/integration-tests/*` |

## 放置规则

- Spring Framework 生命周期、事务同步、调度、事件发布、MVC API 和 Spring 侧 client wrapper 位于 `../../../../jfoundry-runtime-integrations/jfoundry-spring/runtime`。
- Spring Boot 条件、`@ConfigurationProperties`、Bean 装配、metadata 和 `AutoConfiguration.imports` 位于 `../../../../jfoundry-runtime-integrations/jfoundry-spring/autoconfigure/jfoundry-spring-boot-autoconfigure`。
- Spring 中间件和 Testcontainers 验证位于 `jfoundry-runtime-integrations/jfoundry-spring/integration-tests`。
- Quarkus 运行时、中间件、Testcontainers 和 Native Image 验证位于 `jfoundry-runtime-integrations/jfoundry-quarkus/integration-tests`。
- Helidon CDI 生命周期、JTA、JAX-RS、调度和 JPA 集成位于 `jfoundry-runtime-integrations/jfoundry-helidon/runtime`；运行时、中间件、Testcontainers 和 Native Image 验证位于 `jfoundry-runtime-integrations/jfoundry-helidon/integration-tests`。Helidon 没有 JFoundry deployment 模块或 starter 层。
- Starter 只是依赖入口，不得承载运行时行为。Domain 与 Application starter 保持在
  `jfoundry-core/jfoundry-starters`；运行时无关的基础设施 adapter starter 直接位于
  `jfoundry-core/jfoundry-starters/infrastructure`。其 artifactId 使用能力与技术名称，例如
  `jfoundry-persistence-jpa-starter`；不得增加中间聚合 POM 或继续增加目录层级。
- 运行时无关的数据库、serializer 和 scheduler adapter 位于 `jfoundry-core/jfoundry-infrastructure`。
- Broker client `MessageSender` adapter 位于各自的运行时集成；应用层 `MessageSender` 与 `SendResult` 契约仍保持运行时无关。
- 运行时特定的中间件集成测试和 Testcontainers 兼容性验证位于相应运行时的 `integration-tests` 目录；运行时无关测试紧邻其所验证的 core 或 infrastructure 实现。

## 可靠消息边界

`jfoundry-outbox-core` 拥有消息模型、store 契约、派发服务、重试/退避契约和状态机。

`jfoundry-outbox-spring` 拥有 Spring 运行时集成，例如事务同步、scheduled dispatch 和 Spring 运行时中的领域事件记录。

`jfoundry-spring-boot-autoconfigure` 拥有 Outbox 配置项、条件和 Bean 装配。`OutboxDispatcherProperties` 及关联属性位于这里，因为属性绑定属于 Boot 职责。

`jfoundry-outbox-jobrunr` 是纯 JobRunr 派发 adapter；它的 Spring Boot 自动配置也属于 `jfoundry-spring-boot-autoconfigure`。

`jfoundry-outbox-jpa` 和 `jfoundry-inbox-jpa` 是运行时无关的 Jakarta Persistence adapter。它们实现 Outbox 和 Inbox store SPI，不要求 Spring 或 Spring Boot。它们的 Spring Boot starter，即 `jfoundry-outbox-jpa-spring-boot-starter` 和 `jfoundry-inbox-jpa-spring-boot-starter`，是显式能力选择；通用 `jfoundry-persistence-jpa-spring-boot-starter` 只提供业务 JPA 运行时装配，不会引入任一 store。

实现机制和数据库限制属于 [JPA 实现指南](../implementations/jpa.md)。能力状态模型和 SQL 模板策略属于[可靠消息](../capabilities/reliable-messaging.md)。

## 验收标准

- Core 模块对 Spring、Spring Boot、Helidon、Quarkus、Micronaut、CDI、Jakarta runtime API、broker client 和持久化框架细节没有 compile/provided 依赖。
- Adapter 模块不得直接注册 Spring Boot 自动配置。
- Starter 保持为轻量依赖选择。
- 未来运行时集成可以复用 core SPI 和运行时无关 adapter，而不依赖 Spring Boot。
