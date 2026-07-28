# 运行时安全与可观测性边界决策记录

状态：已接受，可进入后续设计

## 目的

本文档记录 jfoundry 在评估运行时安全、审计和可观测性能力时已经达成的工作决策。它不是公开使用文档，也不构成公开 API 承诺。

## 安全决策

现阶段不为 jfoundry 增加通用 `ExecutionContext` 或框架级安全模块。

安全责任按以下方式划分：

- 身份认证和令牌校验属于入端运行时适配器职责。
- 身份提供方负责登录、账号生命周期、令牌签发和撤销。
- API 访问控制属于所选运行时集成及应用策略。
- 保护业务不变量的业务授权，应在应用服务和领域行为中保持显式。
- 领域层不得依赖 JWT、claims、Spring Security 或隐式安全上下文。

## 理由

通用执行上下文会与 Spring Security、Micrometer/OpenTelemetry 以及运行时特定的上下文传播重叠。在没有多个具有相同稳定语义的框架无关消费者时，它会演变成 claims、权限、租户状态和追踪数据的隐式全局容器。

原 `leistd-security` 模块只是需求来源，不是设计模型。它混合了 JWT claim 映射、Spring Security 配置、当前用户 `ThreadLocal` 状态、MVC 和 Feign 请求头传播、临时任务身份以及 MDC 日志。其中多项选择具有公司特定性或运行时特定性。

## 重新评估条件

仅当以下条件同时满足时，才重新评估运行时无关的执行上下文：

1. 至少两个独立、跨运行时的 jfoundry 能力需要相同的执行元数据。
2. 更窄的能力专用 SPI 无法表达该需求。
3. 该模型排除 JWT、任意 claims、角色、框架对象和业务用户模型。
4. HTTP、消息和定时任务生命周期能够使用同一套可文档化、可测试的语义。

## 审计设计方向

技术审计元数据、业务溯源和合规审计轨迹是三个不同的关注点：

- 当影响业务含义时，业务溯源应明确建模在领域状态或领域事件中。
- 技术审计元数据表示当前持久化快照，应使用 `Instant`、可测试的 `Clock` 和稳定的操作者标识。
- 合规审计轨迹是仅追加的历史证据，应保持为独立的可选能力。
- 软删除独立于技术审计和合规审计。

## 技术审计重构

技术审计是持久化快照，不是领域状态。删除领域层的 `Auditable`、`AuditableEntity` 和 `AuditableAggregateRoot`；软删除保持为独立能力，不再与技术审计捆绑。

- 持久化核心定义的审计快照只包含 `createdAt`、`createdBy`、`lastModifiedAt` 和 `lastModifiedBy`。
- 时间统一使用 `Instant`，操作者只保存稳定 ID，不保存显示名称。
- `AuditActorProvider` 和注入的 `Clock` 构成框架无关输入；运行时适配器负责从安全、消息或任务入口取得操作者。没有操作者时保存 `null`，不得伪造 `unknown`。
- `AuditStamping` 统一插入和更新语义：插入时填充创建与最后修改字段，实际更新时只更新最后修改字段。
- JPA 与 MyBatis-Plus 使用各自适合的回调或填充机制实现相同语义；ORM API 不构成框架审计契约。

业务上有意义的提交人、审批人等由领域模型显式表达；合规操作审计继续作为独立的仅追加能力。项目仍处于开发阶段，不为现有审计类型保留兼容约束。

## 明确非目标

- 构建授权服务器、登录服务、账号目录或令牌签发器。
- 定义通用 JWT claim 或角色模型。
- 增加用于生产环境的本地锁实现。
- 增加全局成功响应包装、通用 DTO 工具或公司特定的请求头传播。

## 可观测性方向

jfoundry 将使用 OpenTelemetry 作为可观测性的主要互操作模型。它是 traces、metrics、logs 和上下文传播的厂商无关、跨运行时标准。

- `jfoundry-domain` 和 `jfoundry-application` 不得依赖 OpenTelemetry、Micrometer、日志 API 或运行时上下文 API。
- 后续 OpenTelemetry 埋点必须是显式的可选集成，并描述框架特有操作，例如 Outbox 持久化和派发、Inbox 去重、锁获取和任务执行。
- 运行时集成可以使用其原生观测机制。Spring 集成可通过 Micrometer Observation 互操作，但 Micrometer 不是 jfoundry 的跨运行时公开观测契约。
- SDK 生命周期、exporter、collector、采样、遥测后端和全局 provider 配置由使用方应用和运行环境负责，而不是 jfoundry。
- jfoundry 不引入自己的 trace identifier、`ThreadLocal` 传播设施或 MDC 生命周期管理；需要链路关联的集成应使用 OpenTelemetry context 和标准传播机制。
- 埋点必须避免与运行时、HTTP、数据库或消息客户端已经产生的遥测重复。
- metric attributes 必须有界且保持低基数。聚合标识、消息标识、操作者标识、令牌值和任意异常文本都不能作为 metric attributes。

异步可靠消息的传播模型需要单独设计：事件可能在一个事务中持久化，随后由工作线程派发并在其他位置消费。该设计必须保持关联性，同时不能让追踪元数据成为领域关注点。

不会增加泛化的遥测事件总线。可选 OpenTelemetry 集成应在既有能力边界完成埋点；仅当该边界无法获得必要的技术事实时，才增加窄范围、能力专用的 SPI。

## 异步可靠消息追踪传播

对于使用可选 OpenTelemetry 集成的 Outbox，采用 W3C Trace Context 实现跨事务、跨线程和跨服务的链路关联：

- 在 Outbox 持久化时，从当前观测上下文捕获 `traceparent`、`tracestate` 等追踪上下文，并作为内部技术元数据保存。
- 后台派发时恢复该上下文，创建短暂的派发 span，并向 Broker message headers 注入标准追踪上下文；消费端通过标准消息传播继续关联。
- 持久化、每次派发和每次消费都是独立的短 span；不得以一个 span 覆盖消息在 Outbox 中的等待时间。
- 每次重试创建独立的派发 span，并关联原始调用链；没有上游上下文时，派发任务形成新的根 trace。
- 追踪上下文在逻辑上独立于领域事件载荷和业务消息 headers。Outbox 使用可空的 `traceparent` 和 `tracestate` 两个受长度限制的技术字段保存它们；不使用 JSON blob，也不提供任意 header 存储。
- 默认不自动持久化或传播 `baggage`。它可能携带敏感或业务数据，应由使用方在明确授权和治理后自行处理。
- 追踪上下文不是身份、授权凭据、业务幂等键或可信输入，不能用于安全或业务决策。

## Outbox 操作语义

Outbox 可观测性只定义两个主要操作：

- `outbox.persist`：领域事件外部化并写入 Outbox 的过程。
- `outbox.dispatch`：一次领取、序列化、发送并等待 Broker 确认的派发尝试。

一次重试是新的 `outbox.dispatch`，而不是第三种操作。成功、可重试失败和最终失败作为派发结果或 span event 表示。空轮询不创建 trace，只产生低成本指标。

## 诊断信号分工

- traces 用于定位单次 `outbox.persist` 或 `outbox.dispatch` 的耗时、失败原因和跨服务关联。
- metrics 用于监控积压量、最老待发事件年龄、派发成功或失败量以及派发耗时，并承载告警。
- logs 只记录最终失败、异常重试或阈值越界等需要人工排查的事件；不得为每次成功派发记录日志。

消息积压应由 metrics 触发告警，再通过 logs 定位异常记录，最后使用 trace 还原单次链路。

## Outbox 积压快照

全局积压不能从派发循环推算。增加可选的 `OutboxBacklogReader`，作为不依赖 OpenTelemetry 的窄范围只读 SPI。它由支持的持久化适配器以有索引的聚合查询实现，并只返回：

- 当前可派发事件数量；
- 延迟重试事件数量；
- `DISPATCHING` 事件数量；
- 最老可派发事件的发生时间。

OpenTelemetry 集成以受控周期读取并缓存该快照，再输出 gauge；不得在每次监控后端抓取或每轮空轮询时直接查询存储。未实现该 SPI 的第三方存储适配器仍可使用 Outbox，也仍可提供派发成功、失败和耗时指标，但不提供全局积压 gauge。

## Outbox 最小指标集

Outbox 只提供以下最小指标语义：

- 按 `published`、`retry_scheduled` 和 `dead_lettered` 结果区分的派发尝试计数；
- 按相同结果区分的派发耗时直方图；
- 按 `ready`、`scheduled_retry` 和 `dispatching` 状态区分的积压 gauge；
- 最老可派发事件年龄 gauge；
- 卡死派发恢复计数。

事件类型、聚合标识、消息标识、租户、用户、Broker topic、异常文本和异常类型不得作为 metric attributes。服务名、环境和实例等资源信息由运行环境统一提供，不由 jfoundry 重复附加。实际的 OpenTelemetry metric name、unit 和 instrument 类型在实现设计阶段按当时的 OpenTelemetry 规范校验。

## Inbox 正确性重构方向

Inbox 的职责是消息幂等处理与处理所有权协调，不是自带调度器的重试队列。现有 Inbox 实现尚未满足以下目标模型，后续应允许破坏性重构：

- `PROCESSING` 必须带有 `claimToken`、`claimedAt` 和租约超时；租约过期后，后续投递可以重新领取。
- 成功和失败状态更新必须验证 `claimToken`，防止失效消费者覆盖新一轮处理结果。
- 消息领取不再只返回 `boolean`，而应显式区分已领取、已处理的重复消息和仍由有效租约持有的消息。
- Broker 负责重投、退避和死信策略；Inbox 不复制一套 Outbox 式的计划重试调度。
- Inbox 的后续可观测性设计围绕领取、处理、完成或失败以及租约恢复，而非机械复用 Outbox 的积压模型。

## Inbox 可观测性语义

Inbox 的目标 API 应由返回 `boolean` 的 `executeOnce(...)` 重构为返回明确 `InboxExecutionResult` 的框架无关 `InboxProcessor` 端口。其最终结果只有 `processed`、`duplicate`、`in_progress` 和 `failed`。对实际领取并执行的消息，额外以固定领取来源表示 `fresh`、`failed_retry` 或 `expired_lease`。

可选 OpenTelemetry 集成以 decorator 包装 `InboxProcessor`，而不是引入泛化遥测事件总线。它仅创建 `inbox.process` 这一框架 span，作为 Broker consumer span 的子 span，并记录最终结果和领取来源。指标提供按最终结果聚合的处理次数和处理耗时；`expired_lease` 领取次数作为恢复指标。日志只记录失败及租约恢复异常。

## 分布式锁可观测性方向

锁的原始名称可能包含订单、租户或用户标识，绝不写入 metrics、traces 或 logs。锁核心后续应将任意 `String` 锁名重构为结构化 `LockKey(scope, value)`：只有固定、低基数的 `scope` 可作为观测属性，`value` 只参与实际加锁。

锁可观测性只创建覆盖等待和获取过程的 `lock.acquire` span。指标包括获取次数、获取耗时和持锁耗时，结果仅为 `acquired`、`unavailable` 和 `error`。持锁耗时在释放时记录。`LockTemplate` 与 Spring 注解入口应通过同一个框架无关执行端口，以避免重复埋点。锁 span 不覆盖整个业务回调。

## 任务执行边界

当前 JobRunr 能力只是 Outbox 派发的调度适配器，不是面向业务任务的 jfoundry 框架能力。调度器自身的执行、排队和失败诊断交给 JobRunr 或运行时原生 OpenTelemetry 集成；jfoundry 只保留 `outbox.dispatch` 的框架语义，不能因其由 JobRunr 触发而重复创建 Job span。

只有未来出现跨运行时的业务任务执行端口时，才单独设计其调度、重试、身份和可观测性契约。

## 可靠消息传播元数据

为实现已确认的 W3C Trace Context 传播，Outbox 需要破坏性重构：增加框架内部、不可变且受大小限制的 `MessagePropagation`，并持久化在 Outbox 中。`MessageSender` 应重构为发送结构化 `OutboundMessage`，其中路由、载荷和传播元数据分别建模；各 Broker 适配器将传播元数据映射为实际消息 headers。

OpenTelemetry 集成只在 `MessagePropagation` 中写入和读取 W3C Trace Context。该类型不对使用方开放任意业务 headers 入口，且不得传播 JWT、租户、用户、任意 `baggage` 或公司自定义 headers。

## 观测运行时装配

OpenTelemetry-first 指的是跨运行时的观测语义、W3C Trace Context 传播和后端互操作优先，而不是强制所有运行时直接调用 OpenTelemetry API。

- `jfoundry-domain` 和 `jfoundry-application` 保持不依赖任何观测库。
- 框架无关的 `jfoundry-observability-otel` 基础设施模块只依赖 OpenTelemetry API，提供非 Spring 运行时所需的可选 decorators；它不创建 SDK、全局 provider 或 exporter。
- Spring 运行时默认使用 Micrometer Observation 和 Micrometer Tracing，因为 Spring Boot 能将 Observation 同时接入 metrics 和 tracing。需要跨服务 OpenTelemetry tracing 时，由使用方将 Micrometer Tracing 配置为 OpenTelemetry bridge，并按运行环境配置 OTLP。
- Spring 指标通过 Micrometer 导出；不得在同一 Spring 集成中同时为同一操作调用 OpenTelemetry `MeterProvider` 和 Micrometer，以免重复或产生未导出的指标。
- Quarkus 使用其原生 OpenTelemetry 运行时支持。Helidon 优先使用其 telemetry/tracing 抽象和自动集成；该抽象以 OpenTelemetry 为追踪底层，当前 OpenTelemetry 支持仍为 preview，必须隔离在 Helidon 运行时适配器中。各运行时对同一框架操作只能装配一种观测实现。
- SDK 生命周期、Collector、Exporter、采样、资源属性和后端配置始终由使用方应用与运行环境负责。

## Spring 运行时模块边界

现有 Spring Framework runtime 适配器已按事件、持久化、消息、Outbox、事务、锁和 Web MVC 正确拆分，不再继续细拆。后续将单一的 Spring Boot auto-configuration 模块按能力拆分为基础装配、事件、持久化、消息、Outbox、Inbox、锁、Web MVC 和可观测性等独立模块。

每个能力 starter 只依赖对应 runtime、框架契约和对应 auto-configuration；默认 Spring starter 不携带能力配置。每个 auto-configuration JAR 自己维护 `AutoConfiguration.imports`、配置属性、条件与测试；跨能力组合由 Spring 集成测试模块验证。

## HTTP 错误扩展点

现有静态 `ProblemCatalog` 应重构为运行时无关的 `ProblemMapper` 链：多个 mapper 按优先级解析 `Throwable`，应用 mapper 优先，jfoundry 内置异常映射作为低优先级默认值，未映射的 `Exception` 统一映射为安全的 500 问题响应。不得捕获或吞掉 `Error`。

`ProblemDescriptor` 表达 RFC 9457 的 `type`、`title`、`status`、`detail` 以及不可变扩展成员；`code` 是稳定的 jfoundry 扩展字段，请求路径 `instance` 由 Web 运行时填充。扩展成员必须可安全公开且可 JSON 序列化。默认响应不得暴露任意异常消息，应用 mapper 明确决定哪些客户端文本安全。

Spring MVC 的 `ResponseEntityExceptionHandler` 只负责将解析后的 descriptor 渲染为 `ProblemDetail`。应用可注册跨运行时 `ProblemMapper`，也可保留原生 `@ControllerAdvice` 处理纯 Web 特例；Quarkus 和 Helidon 复用相同 mapper 链并各自渲染。框架不提供全局成功响应包装，也不定义业务系统通用错误码体系。现有 HTTP 状态默认映射应重审并修正错误语义。

## 下一项

按已确认的边界制定分阶段实施计划，并在每个阶段完成 API、适配器、自动配置、SQL 模板和运行时集成验证。
