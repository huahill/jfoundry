# 采用就绪度与已验证范围

本文记录 jfoundry 与相关领域架构插件能否支撑真实业务项目开发的阶段性评估。它以可复现证据说明
采用范围和前置条件，不是通用的生产就绪认证。评估日期为 2026-07-31。

## 相关仓库

| 仓库 | 职责 |
|------|------|
| [xfoundries/jfoundry](https://github.com/xfoundries/jfoundry) | 面向 DDD 业务应用的可选 Java 运行时与实现框架 |
| [xfoundries/domain-architecture-skills](https://github.com/xfoundries/domain-architecture-skills) | 负责领域建模、架构决策和可选 jfoundry 落地的设计期插件 |
| [xfoundries/jfoundry-expense-approval-demo](https://github.com/xfoundries/jfoundry-expense-approval-demo) | 使用刻意简化的业务领域验证完整架构和集成链路的端到端项目 |

插件与框架承担不同职责。即使项目不选择 jfoundry，插件仍然可以提供领域和架构指导。jfoundry
应在领域、架构、项目形态和运行时约束明确后，作为可选实现方案进入项目。

## 当前判断

| 项目 | 当前判断 | 建议定位 |
|------|----------|----------|
| `domain-architecture-skills` | 可以作为设计期指导直接用于真实项目 | AI 辅助开发的标准领域与架构工作流，但业务含义仍需人工负责 |
| jfoundry | 可以在已验证技术栈内受控用于真实项目 | 可选业务框架，固定不可变版本，并通过具体项目的生产准入检查 |
| 领域架构插件 + 可选 jfoundry 落地 | 已通过分别维护的 Hexagonal 与 Onion Simple 变体，证明可以从需求、建模、架构选择和可选框架落地，一直支撑到实现与验收 | 优先用于 Java 25、Spring Boot、MyBatis-Plus、PostgreSQL、Kafka 和 Redis 项目 |

表格最后一行具体指以下顺序：

```text
业务需求
  -> Domain Architecture 插件完成框架中立的领域建模
  -> 插件显式决定架构风格和项目形态
  -> 仅在选择 jfoundry 时进入 using-jfoundry 实现指导
  -> 使用已选运行时和适配器完成业务实现
  -> 架构测试、集成测试和端到端验收
```

它不是另一个外部流程产品，也不表示 jfoundry 是必选项。插件负责设计期决策；只有这些决策完成且
项目明确选择 jfoundry 后，jfoundry 才提供可选的实现契约和适配器。

这个判断支持真实项目采用，但不表示两者构成通用架构，不表示业务项目必须使用 jfoundry，也不表示
所有看似支持的技术组合都已经完成生产验证。

## 项目价值

### jfoundry

jfoundry 不只是项目脚手架。它的主要价值是为业务应用中反复出现的问题提供明确边界和可复用契约：

- 运行时框架无关的领域与应用契约，使 Spring、ORM、HTTP 和消息中间件 API 不进入业务核心。
- 通过 jMolecules 语义和 ArchUnit 规则，把项目选择的 DDD、Hexagonal、Onion、CQRS 约束变成
  可执行规则，而不只停留在包名和文档中。
- 事务边界、聚合持久化生命周期、乐观并发、持久化异常翻译、领域事件、Outbox、Inbox、消息 SPI
  和分布式锁减少了业务项目的重复基础设施设计。
- 按能力拆分模块和启动器，使持久化、消息中间件、Outbox、Inbox 和锁保持可选。
- 多表聚合的从表同步具有业务特性时，仍由业务适配器明确表达。框架不通过反射推导聚合还原，也不
  用 ORM 魔法隐藏这项责任。

### Domain Architecture 插件

插件处理的是另一类风险：AI Agent 可以生成结构上合理的代码，却做出错误的领域或架构决策。它的
价值是让决策顺序和责任归属保持明确：

- 从需求和业务语言进入领域建模，再进入框架落地。
- 分别识别 DDD 核心纪律、架构风格约束、框架约定、启发式建议和项目策略。
- 只有业务上下文确实需要时才选择 Hexagonal、Onion、Layered 或 CQRS。
- 简单 CRUD 可以保持简单，jfoundry 始终可选。
- 阶段结果和 Handoff 会保留假设、阻塞项、证据和开放问题，供后续规划、实现和评审使用。

架构指导无法发现需求中没有提供的业务事实，也不能替代对业务结果负责的人工评审。架构测试可以
验证声明过的静态边界，但不能证明聚合、不变量或限界上下文正确表达了业务。

## 验证证据

[费用报销审批 Demo](https://github.com/xfoundries/jfoundry-expense-approval-demo) 刻意保持业务逻辑
简单，同时覆盖完整架构与集成链路。同一套业务规则、数据库模型、集成契约和验收场景已通过分别维护
的 Hexagonal 与 Onion Simple 变体验证。截至评估日期，已记录的证据包括：

- jfoundry 在 Java 25 下完成 67 模块测试矩阵，其中包括基础运行时、MyBatis-Plus、Redisson 和
  JobRunr 能力的 Spring Boot 原生镜像验证。
- 插件发布的全部技能、Codex 插件清单和 Claude 市场元数据通过校验。
- 两个架构变体均通过同一套完整自动化测试，其中包含 5 个基于容器的端到端场景。
- 端到端环境包含两个独立 PostgreSQL、Kafka、Redis 和两个 Spring Boot 应用上下文。
- JPA 与 MyBatis-Plus Outbox/Inbox 存储均通过 PostgreSQL、MySQL 的 Testcontainers 验证，其中包括数据库相关的 JPA Inbox 领取策略。
- 覆盖支付成功与失败、Inbox 重复投递、并发月度额度控制，以及事务回滚且不写审批 Outbox。
- 另外独立执行了从 HTTP 审批、经过 Kafka、最终形成 `PAID` 查询投影的本地完整链路。
- 费用报销审批 Demo 的两个 Spring Boot 应用均构建为 GraalVM 原生镜像，并通过同一套五个基于容器的
  端到端消息场景。
- Onion 验证覆盖显式领域、应用、基础设施环、向内依赖规则、DDD Repository
  位置、职责优先的应用契约命名，以及与 Hexagonal 变体相同的按需 CQRS 结构。

| 验证对象 | 已验证证据 | 不能由此推出 |
|----------|------------|--------------|
| Hexagonal 变体（`main`） | 显式主端口/次端口与适配器、能力内嵌方向包、中立的应用层共享视图、严格 ArchUnit 规则 | Hexagonal 是默认架构，或每个应用都需要全套角色 |
| Onion Simple 变体（`onion-architecture`） | 显式领域/应用/基础设施环、向内依赖、DDD 与职责优先命名、同一套验收测试 | Onion 不定义 Port/Adapter 语义，或包级环已提供 Maven 模块隔离 |
| 共同业务基线 | 相同聚合行为、数据库模型、集成契约、CQRS、Outbox/Inbox、锁、中间件拓扑和验收场景 | 两种风格可互换、应混用，或在所有项目中具有相同组织权衡 |

Demo 不是只确认最初设计，也实际暴露了框架和指导缺陷。相应修复涉及聚合持久化跟踪、乐观并发、
异常边界、Outbox 集成契约、PostgreSQL Inbox 幂等、可移植 JSON、消息代理选择、自动装配顺序和
Spring AOP 代理基础设施，也包括架构风格语义、能力优先包结构，以及将架构中立 CQRS 规则与
Hexagonal Port/Adapter 约定分离。

## 已验证范围

当前最强证据适用于：

- Java 25 业务应用。
- Spring Boot 4.0.x 和 Spring Framework 7.0.x，包括 JVM 中间件集成，以及基础运行时、
  MyBatis-Plus 持久化、Redisson 锁和 JobRunr Outbox 派发的 GraalVM 原生镜像验证。费用报销审批
  Demo 还使用两个原生可执行文件验证 Kafka、Outbox、Inbox、PostgreSQL 和支付投影链路。
- Quarkus 3.37.3 的 CDI 发现与 Jakarta Transactions `TransactionRunner` 集成，已具备 JVM 运行时集成验证
  和原生镜像 CI 验证任务。
- Helidon MP 4.5.1 的 CDI/JTA、JPA 装配、JPA Outbox/Inbox 存储、调度与 JAX-RS Problem Details。
  其 GraalVM 25 使用方已验证 CDI/Web 启动和 Problem Details 响应；原生 JTA 与 JPA 仍受上游实验性
  限制，可复现的失败记录见 [Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015)。
- MyBatis-Plus 业务持久化与 PostgreSQL。
- JPA 与 MyBatis-Plus Outbox/Inbox 存储与 PostgreSQL、MySQL。
- Kafka 集成、Redis/Redisson 分布式锁、事务性 Outbox 和消费端 Inbox。
- DDD 建模、分别验证的 Hexagonal 或 Onion Simple 架构，以及不使用
  事件溯源的按需 CQRS。
- 使用独立集成契约和运行时装配模块的 Maven 多模块项目。

没有验证其他运行时，不会阻止项目在上述技术栈中采用；它只表示不能在缺少同等测试证据时，把结论
自动外推到其他运行时。

## 边界与风险

当前证据尚不能确认：

- Onion 环拆分为独立 Maven 模块后的编译期隔离。本次 Onion 变体是在一个应用模块内使用
  包级环和 ArchUnit 依赖规则完成验证。
- 在已有生产项目中切换架构风格的迁移成本或组织效果。本 Demo 验证的是两个分别维护的变体，
  不是生产迁移。
- Helidon 消息代理投递、Redisson 锁、JobRunr 和可工作的 Helidon 原生 JTA/JPA 路径。不得从 Spring 或
  Quarkus 能力自动推断这些能力。Micronaut 尚无运行时集成。
- 其他 ORM、数据库或消息中间件组合。
- 任意下游依赖图、部署目标或应用配置的原生镜像兼容性。使用方必须针对实际选择的能力和构建期配置
  完成验证，尤其是会影响 AOT Bean 注册的结构化属性。
- 应用安全、可观测性、部署、容量、性能、灾难恢复和长期生产运行。
- 下游项目从一个 jfoundry 稳定版本升级到另一个稳定版本的兼容性。

截至评估日期，当前 Reactor 版本仍为 `1.0.0-SNAPSHOT`。仓库已经具备发布自动化和明确的
[兼容矩阵](../../../release/compatibility.md)，但还没有稳定版本 tag。生产项目不应依赖持续变化的
SNAPSHOT，而应使用正式发布版本或由组织内部治理的不可变构建。

Domain Architecture 插件已经具备结构化工作流、参考资料、模板和格式校验，但还没有自动化场景评测
套件，用来系统验证 Agent 在正确和错误输入下的决策。费用报销 Demo 为同一业务与运行时路径上的
两个架构变体提供了较强集成证据，但不能替代插件的完整行为基准。

Spring Framework 7 计划移除基于约定的复合注解属性覆盖。jMolecules 上游讨论
[xmolecules/jmolecules#153](https://github.com/xmolecules/jmolecules/issues/153) 仍处于开放状态。
jfoundry 不应为了兼容而在运行时框架无关的架构模块中引入 Spring `@AliasFor`。

## 采用建议

真实业务项目可以按以下方式采用：

1. 使用 Domain Architecture 插件完成从需求到领域模型和架构决策的过程，同时由项目团队对业务含义
   承担最终评审责任。
2. 只有在架构、运行时、持久化、消息和项目形态适合时才选择 jfoundry，不把采用框架作为领域建模
   的前置条件。
3. 先在中等重要度的业务服务中使用，再决定是否将 jfoundry 扩展为组织级标准，或用于最高关键等级
   的资金链路。
4. 通过合适的 BOM 固定不可变 jfoundry 版本，不使用持续变化的 SNAPSHOT 上线。
5. 使用项目实际选择的数据库、消息中间件、部署拓扑和故障策略，重新运行完整应用验收链路。
6. 增加项目自有的认证授权、密钥、审计、指标、链路追踪、健康检查、告警、迁移与回滚、备份恢复、
   负载和故障注入准入项。
7. 保留明确的退出路径：jfoundry 持久化基类是可选支持，适配器可以直接实现端口，低复杂度模块也
   可以采用更简单的应用模式。

## 结论

在已验证技术栈内，jfoundry 与 Domain Architecture 插件足以支撑 AI Agent 开发一个完整的、采用
DDD、分别验证的 Hexagonal 或 Onion Simple 架构风格、按需 CQRS 和可靠消息链路的业务项目。这不
表示两种风格可以混用，也不表示可以跳过显式架构决策。插件已经可以作为真实项目的架构治理工具；
jfoundry 已达到受控真实项目可用，但在把它作为组织级默认生产框架之前，仍需要稳定的不可变版本和
具体项目的非功能验证。

稳定版本发布、下游升级验证、其他运行时、基础设施组合或架构风格获得同等级证据，或者生产准入
条件发生实质变化时，应同步更新本文评估。
