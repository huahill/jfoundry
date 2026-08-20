# 能力目录

先按业务所需能力选择 JFoundry，再选择对应运行时的接入依赖。下表列出的都是使用方依赖；除非应用明确实现自己的运行时集成，否则不要直接引入内部核心模块或适配器模块。

每个应用都应按[接入指南](../integration/getting-started.md)导入 `jfoundry-dependencies` 与对应运行时 BOM。矩阵给出下一步应添加的依赖；详细指南会说明所需的配套模块、存储、传输方式和配置。

| 能力 | 适用场景 | Spring Boot | Quarkus | Helidon MP | 指南 |
|---|---|---|---|---|---|
| 领域建模 | 业务模型需要聚合、值对象、领域事件与显式不变量。 | `jfoundry-domain-starter` | `jfoundry-domain-starter` | `jfoundry-domain-starter` | [接入指南](../integration/getting-started.md) |
| 应用服务 | 用例需要清晰的应用边界、CQRS 契约或领域事件编排。 | `jfoundry-application-starter` | `jfoundry-application-starter` | `jfoundry-application-starter` | [接入指南](../integration/getting-started.md) |
| 可执行架构规则 | 项目需要可复用的 ArchUnit 检查来约束 Hexagonal 或 Onion 边界。 | `jfoundry-architecture-test`（测试范围） | `jfoundry-architecture-test`（测试范围） | `jfoundry-architecture-test`（测试范围） | [ArchUnit 架构规则](../framework/archunit-rules.md) |
| 应用事务 | 用例需要运行时事务边界。 | `jfoundry-spring-boot-starter` | `jfoundry-quarkus-runtime` | `jfoundry-helidon-runtime` | [应用事务](application-transactions.md) |
| 聚合持久化 | 聚合需要 JPA 或 MyBatis-Plus 持久化，同时不把 Repository 变为通用查询接口。 | `jfoundry-persistence-jpa-spring-boot-starter` 或 `jfoundry-persistence-mybatis-plus-spring-boot-starter` | `jfoundry-persistence-jpa-quarkus-runtime` | `jfoundry-persistence-jpa-helidon-runtime` | [聚合持久化](aggregate-persistence.md) |
| Web | HTTP API 需要 RFC 9457 问题响应，或者 Spring 应用需要显式选择的出站 `RestClient` 支持。 | Problem Details：`jfoundry-webmvc-spring-boot-starter`；HTTP 客户端：`jfoundry-web-spring-boot-starter` | Problem Details：`jfoundry-web-quarkus-runtime` | Problem Details：`jfoundry-web-helidon-runtime` | [Web](web.md) |
| 直接消息投递 | 应用需要发布到消息代理，但不需要可靠的 Outbox 记录。 | `jfoundry-messaging-spring-boot-starter` 加一个消息代理启动器 | `jfoundry-messaging-kafka-quarkus-runtime` 或 `jfoundry-messaging-rabbitmq-quarkus-runtime` | 暂未提供 | [消息传输](message-delivery.md) |
| 可靠消息 | 消息必须事务性记录、延后派发或被幂等处理。 | `jfoundry-outbox-spring-boot-starter` 或 `jfoundry-inbox-spring-boot-starter` | `jfoundry-outbox-quarkus-runtime` 或 `jfoundry-inbox-jpa-quarkus-runtime` | `jfoundry-outbox-helidon-runtime` 或 `jfoundry-inbox-jpa-helidon-runtime` | [可靠消息：Outbox 与 Inbox](reliable-messaging.md) |
| 分布式锁 | 数据库约束与幂等仍不足时，用例需要跨实例协调。 | `jfoundry-lock-redisson-spring-boot-starter` | 暂未提供 | 暂未提供 | [分布式锁](distributed-locks.md) |
| 可观测性 | 框架操作需要受限维度的指标与追踪，且不能暴露业务标识。 | `jfoundry-observability-spring-boot-starter` | `jfoundry-observability-otel` | `jfoundry-observability-otel` | [可观测性](observability.md) |

“暂未提供”表示 JFoundry 当前没有发布该运行时的装配模块。应用仍可围绕框架无关契约实现自己的外层适配器；这不构成隐含的支持声明。

## 选择规则

- 只选择业务用例实际需要的能力入口。基础运行时集成不会隐式引入持久化、消息代理、Outbox、Inbox、锁或调度。
- 某项能力可能需要显式选择配套项。例如 Outbox 运行时还需要选定存储和真实 `MessageSender`；持久化入口还需要应用的数据源与迁移。
- 运行时指南用于查询运行时专属配置与替换规则，不是选择能力的主目录。
- 在将某个运行时与能力组合视为生产支持前，先查看[采用就绪度与已验证范围](../integration/adoption-readiness.md)。
