# Repository 与读侧契约迁移指南

本文面向正在从 Active Record、MyBatis-Plus `IService`、通用 Wrapper 或规约模式迁移到 jfoundry 的业务项目，说明什么时候应保留聚合 Repository，什么时候可以考虑拆到读侧端口。

这是一份建模和迁移指南，不要求业务项目为每个查询机械创建接口。查找、读模型、维护和投影物化
是职责分类，不是强制后缀。在 Hexagonal 项目中，这些契约可以作为 Secondary Port；Onion 没有
Port 角色体系。`Reader`、`Store`、`Finder`、`Provider` 等后缀只是按职责命名的 Java 项目约定，
不是 DDD、Onion 或 JFoundry 的官方模式。命名应首先来自通用语言，不能把映射器或服务原样包一层后只改一个后缀。

## 核心区分

Repository 表示某类聚合的集合。它的核心职责是按标识或业务身份加载聚合、保存聚合、删除聚合，并配合聚合保护业务不变量。

读侧契约表示应用核心所需的读取能力。它可以读取数据库、搜索引擎、缓存、远程服务或组合数据源，但这些技术细节由基础设施实现隐藏。

维护契约表示后台扫描、清理、批处理候选选择等技术性或运维性能力。它通常不返回完整聚合，而是返回待处理 ID、时间窗口或轻量候选项。

业务项目可以沿用团队已有的 `QueryPort`、`Reader`、`Finder`、`Gateway`、`Scanner` 等命名，但
`QueryPort` 属于 Hexagonal 或项目本地词汇。无论采用哪个后缀，都必须先让业务对象和职责清楚，
并避免聚合 Repository 退化成通用查询服务。

## Repository 适用场景

以下方法可以保留在聚合 Repository：

- 按聚合 ID 加载：`findById(OrderId id)`。
- 按稳定业务身份定位聚合：`findByOrderNo(OrderNo orderNo)`、`findByTenantCode(TenantCode code)`。
- 命令流程中定位聚合，随后立即执行业务行为：查找当前进行中的操作记录后补写结果、查找可重试任务后切换状态、查找资源池条目后申请或释放资源。
- 聚合生命周期维护：删除某个聚合的附属记录、按聚合身份移除已失效对象。

Repository 方法应优先表达领域意图，而不是复制 SQL 条件。`findCurrentOperation(...)`、`findRetryableTask(...)` 通常比 `findLatestByEnvIdAndStatusInOrderByCreatedTimeDesc(...)` 更合适。

## 查找契约适用场景

查找契约适合应用服务为了执行命令流程或进行领域决策而准备业务事实，但读取结果不承担聚合行为。
它是命令侧的支持输入，不会仅因为只读就成为查询侧视图。优先按所提供的业务事实命名，例如
`AccountContextFinder`；`LookupPort` 只适合明确采用该命名约定的 Hexagonal 项目。

典型场景：

- 外部 SDK 调用前读取环境编码、应用键、租户编码等上下文。
- 命令流程需要确认某个关联对象是否存在，但不会修改该对象。
- 应用服务需要按业务键读取轻量对象，用于权限裁剪、参数转换或流程分支。
- 一个流程需要跨多个聚合读取数据，但读取结果只是输入资料，不是要被当前用例修改的聚合。

方法返回值应尽量是轻量 DTO、record 或专门的查找结果，而不是 MyBatis 数据对象。

## 查询与读模型契约适用场景

查询或读模型契约适合查询用例、页面展示、报表、列表和统计。它只读：返回为查询用例优化的 DTO 或读模型，但不负责物化或更新这些模型。优先按业务视图和职责命名，例如 `ExpenseClaimViewReader`。只有明确采用对应词汇的项目才需要 `QueryPort` 或 `ReadModelPort` 后缀。

典型场景：

- 分页列表、下拉选项、详情页组合展示。
- 仪表盘、报表、统计汇总。
- 最近操作时间、最新采样值、按条件聚合后的展示数据。
- 查询结果形状明显不同于写模型聚合。

如果项目使用 CQRS，`@QueryModel` 应优先标记应用入口语义上的查询返回模型。Hexagonal
Secondary Port 或 Onion 内环读取契约的返回值可以命名为 `ReadModel` 或 `Summary`，但不需要默认标记为 CQRS QueryModel，避免把入口语义扩散到出站依赖契约。

## 投影物化契约适用场景

当 CQRS 使用事件或状态变化构建、刷新派生读模型时，应把这个读模型更新职责与读取该模型的查询契约分开。例如，`PaymentStatusProjectionStore` 可根据已由命令或事件决定的事实 upsert 派生的支付状态视图，而 `ExpenseClaimViewReader` 在页面查询时读取它。该 Store 不重新决定业务规则，也不修改聚合。

`Projection`、`Projector`、`ProjectionStore` 只适合确实存在这种事件或状态变化驱动的读模型物化时使用。它们不是查询的通用后缀或包名，使用它们也不要求事件溯源：CQRS 读模型可以由普通领域事件或集成事件维护，而不保留事件溯源的写模型。

## 维护契约适用场景

后台维护类查询通常不应为了复用而塞入聚合 Repository。优先使用 `RetryCandidates`、`ExpiredClaimScanner` 等实际职责名称，而不是统一套用 `MaintenancePort` 后缀。

典型场景：

- 查找超时处理中记录。
- 查找过期数据 ID 并批量删除。
- 按时间窗口扫描待重试、待清理、待修复对象。

维护端口优先返回聚合 ID 或轻量候选项。需要执行领域行为时，应用服务再按 ID 加载聚合并调用聚合方法。只有确实是纯技术清理且不涉及业务不变量时，适配器可以直接执行批量删除。

## 迁移判断顺序

从旧 Wrapper、规约或 `IService` 查询迁移时，按以下顺序判断：

1. 这个查询是否为了修改某个聚合？
2. 如果是，是否可以按聚合 ID 或稳定业务身份加载？
3. 查询结果是否是完整聚合，且随后会调用聚合行为？
4. 如果只是为流程准备上下文，创建按所提供事实命名的查找契约。
5. 如果服务页面、列表、报表或统计，创建按业务视图命名的查询或读模型契约。
6. 如果由事件或状态变化物化、刷新派生读模型，创建独立的投影物化契约。
7. 如果服务后台扫描、清理、批处理候选选择，创建按扫描或候选职责命名的维护契约。
8. 如果同一个旧方法同时服务命令和查询，按使用场景拆成两个契约，或一个 Repository 方法加一个读侧契约。

## 反例

以下方法通常不应长期留在聚合 Repository：

- 返回分页对象或页面 DTO 的方法。
- 返回 `View`、`Summary`、`Response`、`Projection` 等明显读投影的方法。
- 为 Dashboard 或报表做 `group by`、`sum`、`count` 的方法。
- 只为了给远程 SDK 拼参数而读取若干字段的方法。
- 暴露 MyBatis-Plus `Wrapper`、`IService`、`Page` 等持久化框架类型的方法。
- 方法名直接堆叠数据库条件，并且看不出领域意图的方法。

## 例外

不是所有非 ID 查询都必须拆出 Repository。业务身份查询、命令流程中的聚合定位、生命周期维护都可以是合理的 Repository 方法。

同样，不是所有只读方法都必须引入 CQRS。查询足够简单时，一个轻量查找或读取契约就可以表达应用所需的读能力。只有当读模型形状、性能、查询复杂度或演进方向明显不同于写模型时，再引入明确的读模型和查询模型。

## 渐进采用

简单项目可以先只区分聚合 Repository 和一个通用读侧契约，例如 `OrderViews` 或
`OrderViewReader`。当查询用途开始分化，再按需要拆出查找、读模型或维护职责。

中等复杂度项目通常值得区分流程上下文读取和页面查询读取。后台任务、补偿、清理、重试较多的项目，再引入维护端口会更清晰。

不要为了命名对称而拆分端口。判断依据应是用例职责、数据形状、变化原因和架构边界，而不是方法数量。

## 推荐落地形态

聚合 Repository 的 DDD 身份独立于所选架构。在 Hexagonal 项目中，它可以同时是 `@SecondaryPort` 并保留在 `domain.repository`；在 Onion 项目中，它是内环定义、由基础设施环实现的契约。不要为了满足某个架构包名约定而复制 Repository 接口。

Hexagonal 项目中可以使用 Port/Adapter 词汇表达以下依赖方向：

```text
Primary Adapter
  -> Primary Port / Application Service
      -> AggregateRepository        # 命令侧聚合加载与保存
      -> AccountContextFinder       # 流程上下文读取，Secondary Port
      -> OrderViewReader            # 查询模型与展示读取，Secondary Port
      -> PaymentStatusProjectionStore # 读模型物化，Secondary Port（仅在需要时）
      -> ExpiredOrderScanner        # 后台扫描与清理候选，Secondary Port
Infrastructure Adapter
  -> implements Secondary Port
```

基础设施适配器可以使用 MyBatis、JPA、SQL、远程 API 或缓存实现这些端口。Onion 项目可以使用相同的职责名称，但通过内外环表达依赖方向，不需要 Port/Adapter 后缀。

当按技术职责分包有助于定位时，聚合生命周期存储放在 `persistence.<aggregate>`，命令侧事实读取放在
`lookup.<feature>`，面向调用方的只读视图放在 `query.<feature>`，事件或状态变化驱动的读模型物化放在
`projection.<feature>`。这只是可选项目约定，不是架构规则；不应为了对称而创建每一种目录。

## 与 ArchUnit 规则的关系

jfoundry 的架构测试不会强制业务项目使用 `LookupPort`、`ReadModelPort`、`MaintenancePort`、`Reader` 或 `Store` 等后缀。

`JFoundryRules.aggregateRepositoryConventions()` 同时识别直接继承 jMolecules `Repository` 和继承 jfoundry `AggregateRepository` 的接口，只守护聚合 Repository 不泄漏通用查询条件、分页 API、持久化服务或映射器类型。它不会根据方法名或返回值后缀判断某个查询是否必须迁移到某类读侧端口。

Hexagonal 和 CQRS 相关规则关注的是端口/适配器依赖方向、CQRS 入口语义位置，以及应用核心不依赖持久化细节。读侧端口如何命名和是否进一步细分，应由业务项目按复杂度渐进决定。
